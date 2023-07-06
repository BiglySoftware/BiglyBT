/*
 * File    : DownloadManager.java
 * Created : 19-Oct-2003
 * By      : parg
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

package com.biglybt.core.download;

import java.io.File;
import java.util.List;

import com.biglybt.core.CoreOperationTask.ProgressCallback;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerFileInfoSet;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.networkmanager.LimitedRateGroup;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.tag.Taggable;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.Download.SeedingRank;
import com.biglybt.pif.download.DownloadAnnounceResult;
import com.biglybt.pif.download.DownloadScrapeResult;

public interface
DownloadManager
	extends Taggable
{
    public static final int STATE_START_OF_DAY  = -1;   // should never actually see this one

    public static final int STATE_WAITING       = 0;
    public static final int STATE_INITIALIZING  = 5;
    public static final int STATE_INITIALIZED   = 10;

    public static final int STATE_ALLOCATING = 20;
    public static final int STATE_CHECKING = 30;

    // Ready: Resources allocated

    public static final int STATE_READY = 40;
    public static final int STATE_DOWNLOADING = 50;
    public static final int STATE_FINISHING = 55;
    public static final int STATE_SEEDING = 60;
    public static final int STATE_STOPPING = 65;

    // Stopped: can't be automatically started

    public static final int STATE_STOPPED 	= 70;
    public static final int STATE_CLOSED	= 71;	// download never *has* this state, just used to inform
    												// when stopping for az closedown

    // Queued: Same as stopped, except can be automatically started

    public static final int STATE_QUEUED = 75;

    public static final int STATE_ERROR = 100;



    public static final int WEALTH_STOPPED          = 1;
    public static final int WEALTH_NO_TRACKER       = 2;
    public static final int WEALTH_NO_REMOTE        = 3;
    public static final int WEALTH_OK               = 4;
    public static final int WEALTH_KO               = 5;
    public static final int WEALTH_ERROR          	= 6;

	public static final int ET_NONE						= DiskManager.ET_NONE;
	public static final int ET_OTHER					= DiskManager.ET_OTHER;
	public static final int ET_INSUFFICIENT_SPACE		= DiskManager.ET_INSUFFICIENT_SPACE;
	public static final int ET_STOP_DURING_INIT			= DiskManager.ET_STOP_DURING_INIT;
	public static final int ET_FILE_MISSING				= DiskManager.ET_FILE_MISSING;

	public static final int EF_WAS_FORCE_START			= 0x01;
	
	public static final Object UD_KEY_STOP_REASON = Download.UD_KEY_STOP_REASON;
	
	public void
	setConstructed();
	
	public boolean
	isConstructed();
	
    public void
    initialize();

    public int
    getState();

        /**
         * For stopping this returns the target state after stopping (stopped/queued)
         * @return
         */

    public int
    getSubState();

    public void
    setStateWaiting();

    public void
    setStateQueued();

    public void
    startDownload();

    public boolean 
    canForceRecheck();

    public boolean
    isForceRechecking();
    
    public void 
    forceRecheck();

    	/**
         * Reset the file download state to totally undownloaded. Download must be stopped
         * @param file
         */

    public void
    resetFile(
        DiskManagerFileInfo     file );

        /**
         * Recheck a particular file. Download must be stopped
         * @param file
         */

    public void
    recheckFile(
        DiskManagerFileInfo     file );

    	/**
    	 * Instructions that file given files need allocation - used when switching file storage types/skipped state  
    	 * Will pause and resume the download if it is active
    	 * @param files
    	 */
    
    public void
    requestAllocation(
    	List<DiskManagerFileInfo>		files );
    
    	/**
    	 * Use with care - introduced to support speed tests whereby we want to avoid checking the
    	 * virtual torrent used for the test
    	 * @param enabled
    	 */

    public void
    setPieceCheckingEnabled(
    	boolean	enabled );

  /**
   * Stop the download manager, and do any file/torrent removals.
   * @param stateAfterStopping
   * @param remove_torrent remove the .torrent file after stopping
   * @param remove_data remove the data file after stopping
   */

    public void
    stopIt(
        int     stateAfterStopping,
        boolean remove_torrent,
        boolean remove_data );

    public void
    setStopReason(
    	String	reason );
    
    public String
    getStopReason();
    
    /**
     * As above but definitely indicates that the stop is for removal (if for_removal is true) and therefore that any removal specific actions
     * such as removing partial files should be performed
     *
     * @param stateAfterStopping
     * @param remove_torrent
     * @param remove_data
     * @param for_removal
     */
    public void
    stopIt(
        int     stateAfterStopping,
        boolean remove_torrent,
        boolean remove_data,
        boolean	for_removal );

    	/**
    	 * 
    	 * @param only_if_active
    	 * @return true -> download was placed into a paused state (including if it was already paused)
    	 */
    
    public boolean
    pause(
    	boolean		only_if_active );

    	/**
    	 * 
    	 * @param auto_resume_time
    	 * @return true -> download was placed into a paused state (including if it was already paused)
    	 */
    
    public boolean
    pause(
    	boolean		only_if_active,
    	long		auto_resume_time );

    public boolean
    isPaused();

    public boolean
    stopPausedDownload();
    
    public void
    resume();

    public int
    getTCPListeningPortNumber();
    
    public long
    getAutoResumeTime();

    public default void
    setAutoResumeTime(
    	long	time )
    {
    }
    
    public GlobalManager
    getGlobalManager();

    public DiskManager
    getDiskManager();

    public DiskManagerPiece[]
    getDiskManagerPiecesSnapshot();
    
  	/**
  	 * @deprecated use getDiskManagerFileInfoSet() instead
  	 */
    public DiskManagerFileInfo[]
    getDiskManagerFileInfo();

    public DiskManagerFileInfoSet getDiskManagerFileInfoSet();

    public int getNumFileInfos();

    public PEPeerManager
    getPeerManager();

    public DownloadManagerState
    getDownloadState();

    public TOTorrent
    getTorrent();

    public TRTrackerAnnouncer
    getTrackerClient();

    public void
    requestTrackerAnnounce(
        boolean     immediate );

    public void
    requestTrackerScrape(
        boolean     immediate );

    public TRTrackerScraperResponse
    getTrackerScrapeResponse();

	public List<TRTrackerScraperResponse>
	getGoodTrackerScrapeResponses();

    public void
    setTrackerScrapeResponse(
        TRTrackerScraperResponse    response );

    public String
    getDisplayName();

        /**
         * returns a name based on the torrent hash or an empty string if torrent invalid
         * @return
         */

    public String
    getInternalName();

    public long
    getSize();

    /**
     * This includes the full path to the torrent file.
     * @return
     */
    public String
    getTorrentFileName();

    public void
    setTorrentFileName(String string);

    /**
     * Returns the file location where we save the torrent, this includes the parent
     * directory and the actual file name.
     */
    public File
    getAbsoluteSaveLocation();

    public File
    getSaveLocation();

		/**
		 * @deprecated Use setTorrentSaveDir(FileUtil.newFile(sPath), false)
		 * @implNote Remove once xmwebui stops using this method
		 */
    default void setTorrentSaveDir(String sPath) {
			setTorrentSaveDir(FileUtil.newFile(sPath), false);
		}

	/**
	 * changes the save directory. Only call this if you know what you are doing!!!!
	 * <p/>
	 * When locationIncludesName, torrent name will also change (Data files will be stored under new_location)<br/>
	 * When !locationIncludesName, only save dire is changed, name remains (Data files will be stored under new_location + / + old_location.getName()) 
	 */
	void setTorrentSaveDir(File _new_location, boolean locationIncludesName);

    public boolean isForceStart();

    public void setForceStart(boolean forceStart);

    public boolean
    isPersistent();

    /**
     * Retrieves whether the download is complete
     *
     * @param bIncludingDND true- include files marked as Do Not Download.<BR>
     *                       false- don't include files marked DND.<p>
     *                       If there are DND files and you choose to include
     *                       DND in the calculation, false will always be
     *                       returned.
     * @return whether download is complete
     */
    public boolean
    isDownloadComplete(boolean bIncludingDND);

    public void
    checkLightSeeding(
    	boolean	full_sync );
    
    public String
    getTrackerStatus();

    /**
     * When the next call to the tracker will occur (in seconds)
     */
    public int
    getTrackerTime();

    public String
    getTorrentComment();

    public String
    getTorrentCreatedBy();

    public long
    getTorrentCreationDate();

    public int
    getNbPieces();

    public String
    getPieceLength();

    public int
    getNbSeeds();

    public int
    getNbPeers();

	/**
     * Checks if all the files the user wants to download from this torrent
     * actually exist on their filesystem.
     * <p>
     * If a file does not exist, the download will be set to error state.
     * @param expected_to_be_allocated if this is false and allocation <b>hasn't<b> been attempted then the method will return with false without marking the download as in an error state
     * @return Whether all the non-skipped (non-DND) files exist
     */

    public boolean
    filesExist(
    	boolean	expected_to_be_allocated );

    /**
     * Download must be stopped - used at start-of-day to recover error state
     * @param errorType
     * @param errorDetails
     */
    
    public void
    setErrorState(
    	int		errorType,
    	String	errorDetails,
    	int		errorFlags );
    
    public String
    getErrorDetails();

    public int
    getErrorType();

    public int
    getErrorFlags();
    
    public DownloadManagerStats
    getStats();

    public int
    getPosition();

    public void
    setPosition(
        int     newPosition );

  	/**
  	 * Retrieve whether this download is assumed complete.
  	 * <p>
  	 * Assumed complete status is kept while the torrent is in a non-running
  	 * state, even if it has no data.
  	 * <p>
  	 * When the torrent starts up, the real complete
  	 * level will be checked, and if the torrent
  	 * actually does have missing data, the download will be thrown
  	 * into error state.
  	 * <p>
  	 * Only a forced-recheck should clear this flag.
  	 *
  	 * @see {@link #requestAssumedCompleteMode()}
  	 */
    public boolean
    getAssumedComplete();

    /**
     * Will set this download to be "assumed complete" for if the download
     * is already complete (excluding DND)
     *
     * @return true- success; false- failure, download not complete
     */
    public boolean
    requestAssumedCompleteMode();

    /**
     * @return the wealthy status of this download
     */
    public int getHealthStatus();

    /**
     * See plugin ConnectionManager.NAT_ constants for return values
     * @return
     */

     public Object[]
     getNATStatus();

        /**
         * persist resume data
         *
         */

    public void
    saveResumeData();

        /**
         * persist any general download related information, excluding resume data which is
         * managed separately by saveResumeData
         */

    public void
    saveDownload( boolean interim );

	/**
	 * To retreive arbitrary objects against this object.
	 */
	public Object getUserData (Object key);

  /** To store arbitrary objects against this object. */
 	public void setUserData (Object key, Object value);

      /**
       * Determine whether disk allocation has already been done.
       * Used for checking if data is missing on a previously-loaded torrent.
       * @return true if data files have already been allocated
       */
    public boolean isDataAlreadyAllocated();

      /**
       * Set whether data allocation has already been done, so we know
       * when to allocate and when to throw a missing-data error message.
       * @param already_allocated
       */

    public void setDataAlreadyAllocated( boolean already_allocated );


    public void setSeedingRank(SeedingRank rank);

    public SeedingRank getSeedingRank();

    public boolean isSwarmMerging();
    
    public String getSwarmMergingInfo();

    public void setMaxUploads( int max_slots );

    public int getMaxUploads();

    public void updateAutoUploadPriority( Object key, boolean inc );

    public int getEffectiveUploadPriority();
    
	/**
	 * Returns the max uploads depending on whether the download is seeding and it has a separate
	 * rate for this
	 * @return
	 */

	public int
	getEffectiveMaxUploads();

        /**
         * returns the currently in force upload speed limit which may vary from the stats. value
         * as this gives the fixed per-torrent limit
         * @return
         */

    public int
    getEffectiveUploadRateLimitBytesPerSecond();

    public void
    setCryptoLevel(
    	int	level );

    public int
    getCryptoLevel();

    	/**
    	 * Progress of a move operation
    	 * @return null: not moving otherwise [ 0->1000, size ]
    	 */
    
    public long[]
    getMoveProgress();
    
        /**
         * Move data files to new location. Torrent must be in stopped/error state
         * @param new_parent_dir
         * @return
         */

    public void
    moveDataFiles(
        File    new_parent_dir )

        throws DownloadManagerException;

    	/**
    	 * At some point someone made the file-move operations stop+restart the download when it was
    	 * designed to work without doing this (see move-on-complete for example). As I don't know the
    	 * reason for this change I've (parg) added a new method to do this in the hope that we might
    	 * in time migrate back to the 'proper' behaviour. grrr
    	 * @param new_parent_dir
    	 * @throws DownloadManagerException
    	 */

    public void
    moveDataFilesLive(
        File    new_parent_dir )

        throws DownloadManagerException;

    public void
    copyDataFiles(
    	File				parent_dir,
    	ProgressCallback	progress )

    	throws DownloadManagerException;

    /**
     * Rename the download - this means the name of the file being downloaded (for single
     * file torrents), or the name of the directory holding the files (in a multi-file torrent).
     *
     * This does not alter the displayed name of the download.
     *
     * @param new_name
     * @throws DownloadManagerException
     */
    public void renameDownload(String new_name) throws DownloadManagerException;

	/**
	 * Move the files and rename a download in one go.
	 *
	 * For convenience - either argument can be null, but not both.
	 *
	 *
	 * @param new_parent_dir new location to move torrent data files to.  
	 * If null, and simple torrent, torrent data file will be renamed new_name.
	 * If null, and not simple torrent, torrent end path will be changed to new_name.
	 *
	 * @param new_name For simple torrent, changes the filename of the downloaded file.
	 * For non-simple torrent, changes the end path  for the downloaded files.
	 * If null, torrent data files will be moved to new_parent_dir, with a subfolder of the existing name.
	 * 
	 * @see #moveDataFiles(File)
	 * @see #renameDownload(String)
	 */
    public void moveDataFiles(File new_parent_dir, String new_name) throws DownloadManagerException;

        /**
         * Move torrent file to new location. Download must be stopped/error
         * @param new_parent_dir
         * @return
         */

    public void
    moveTorrentFile(
        File    new_parent_dir )

        throws DownloadManagerException;

    /**
     * Returns <tt>true</tt> if the download is being saved to one of the default
     * save directories.
     *
     * @since 2.5.0.2
     */
    public boolean isInDefaultSaveDir();

        /**
         * gives the time this download was created (not the torrent but the download itself)
         * @return
         */

    public long
    getCreationTime();

    public void
    setCreationTime(
            long        t );

    public void
    setAnnounceResult(
        DownloadAnnounceResult  result );

    public void
    setScrapeResult(
        DownloadScrapeResult    result );

    public boolean
    isUnauthorisedOnTracker();

    public boolean
    isTrackerError();

	    /**
	     *
	     * @return messaging mode = az, lt or bt
	     */
    public int getExtendedMessagingMode();


        /**
         * Indicates that the download manager is no longer needed
         * @param is_duplicate indicates whether this dm is being destroyed because it is a duplicate
         */

    public void
    destroy(
    	boolean	is_duplicate );

    public boolean
    isDestroyed();

    public PEPiece[]
    getCurrentPieces();

    public PEPeer[]
    getCurrentPeers();

    public List<TrackerPeerSource>
    getTrackerPeerSources();

    	/**
    	 * Gives the download an opportunity to schedule seeding mode piece rechecks if desired
    	 * @return true if a piece has been rechecked
    	 */

	public boolean
	seedPieceRecheck();

	public void
	addRateLimiter(
		LimitedRateGroup	group,
		boolean				upload );

	public LimitedRateGroup[]
	getRateLimiters(
		boolean				upload );

	public void
	removeRateLimiter(
		LimitedRateGroup	group,
		boolean				upload );


	/**
	 * @param listener
	 * @param triggerStateChange
	 *
	 * @since 3.1.1.1
	 */
	public void
	addListener(
		DownloadManagerListener listener,
		boolean triggerStateChange );

    public void
    addListener(
            DownloadManagerListener listener );

    public void
    removeListener(
            DownloadManagerListener listener );

    	// tracker listeners

    public void
    addTrackerListener(
        DownloadManagerTrackerListener  listener );

    public void
    removeTrackerListener(
        DownloadManagerTrackerListener  listener );

    	// peer listeners

    public void
    addPeerListener(
        DownloadManagerPeerListener listener );

    public void
    addPeerListener(
        DownloadManagerPeerListener listener,
        boolean bDispatchForExisting );

    public void
    removePeerListener(
        DownloadManagerPeerListener listener );

		// piece listeners

    public void
    addPieceListener(
    		DownloadManagerPieceListener listener );

    public void
    addPieceListener(
        DownloadManagerPieceListener listener,
        boolean bDispatchForExisting );

    public void
    removePieceListener(
    		DownloadManagerPieceListener listener );

    	// disk listeners

    public void
    addDiskListener(
        DownloadManagerDiskListener listener );

    public void
    removeDiskListener(
        DownloadManagerDiskListener listener );

    public int
    getActivationCount();

    public void
    addActivationListener(
    	DownloadManagerActivationListener listener );

    public void
    removeActivationListener(
    	DownloadManagerActivationListener listener );

    public void
    addTPSListener(
    	DownloadManagerTPSListener		listener );

    public void
    informTPSChanged();
    
    public void
    removeTPSListener(
    	DownloadManagerTPSListener		listener );
    
    public void
    generateEvidence(
        IndentWriter        writer,
        boolean				full );

    public int[] getStorageType(DiskManagerFileInfo[] infos);

    /**
     * @since 3.0.5.1
     */
    public boolean canMoveDataFiles();

    /**
     * Renames the save file, torrent file and the displayed name.
     */
    public void rename(String new_name) throws DownloadManagerException;

    /**
     * @since 3.0.5.1
     */
    public void renameTorrent(String new_name) throws DownloadManagerException;


		/**
		 * Same as renameTorrent, but appends numbers if torrent already exists
		 * @since 4.2.0.9
		 */
	public void renameTorrentSafe(String name) throws DownloadManagerException;

    /**
     * @since 3.0.5.1
     */
    public void moveTorrentFile(File parent_dir, String new_name) throws DownloadManagerException;

    /**
     * Sets the torrent file name (will perform a move of the torrent file) -
     * this shouldn't be used unless you know what you are doing (it requires
     * the download to be stopped!) - it is safer to use moveTorrentFile instead.
     *
     * @since 3.0.5.3
     */
    public void setTorrentFile(File new_parent_dir, String new_name) throws DownloadManagerException;

    /**
     * @since 1.2.0.1
     * @param event_type
     */
    
    public boolean
    canExportDownload();
    
    public void
    exportDownload( File parent_dir ) throws DownloadManagerException;
    
    public default void
    fireGlobalManagerEvent(
    	int		event_type )
    {
    	fireGlobalManagerEvent( event_type, null );
    }
    
    public void
    fireGlobalManagerEvent(
    	int		event_type,
    	Object	event_data );

		/**
		 * Sets the priority for an array for files
		 *
		 * @param fileInfos
		 * @param type
		 * @since 5.6.2.1
		 */
	public void setFilePriorities(DiskManagerFileInfo[] fileInfos, int type);

	public void
	requestAttention();
}