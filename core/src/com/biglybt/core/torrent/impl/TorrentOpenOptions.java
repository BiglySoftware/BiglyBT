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

package com.biglybt.core.torrent.impl;

import java.io.File;
import java.util.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.impl.ConfigurationDefaults;
import com.biglybt.core.config.impl.ConfigurationParameterNotFoundException;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.LocaleTorrentUtil;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;
import com.biglybt.plugin.I2PHelpers;


/**
 * Class to store one Torrent file's info.  Used to populate table and store
 * user's choices.
 * <P>
 * This was copied out of the UI code, and still contains some crap code
 */
public class TorrentOpenOptions
{
	private final static String PARAM_DEFSAVEPATH = "Default save path";

	private final static String PARAM_MOVEWHENDONE = "Move Completed When Done";

	public final static int QUEUELOCATION_BOTTOM = 1;

	public final static int QUEUELOCATION_TOP = 0;

	public final static int STARTMODE_FORCESTARTED = 2;

	public final static int STARTMODE_QUEUED = 0;

	public final static int STARTMODE_SEEDING = 3;

	public final static int STARTMODE_STOPPED = 1;

	/** Where the torrent came from.  Could be a file, URL, or some other text */
	/** @todo: getter/setters */
	public String sOriginatingLocation;

	/** Filename the .torrent is saved to */
	/** @todo: getter/setters */
	public String sFileName;


	private String sDestDir;

	private String manualRename;	// if user has manually renamed the top level folder

	/** for multifiletorrents and change location */
	/** @todo: getter/setters */
	private String sDestSubDir;

	private boolean explicitDataDir;

	/** @todo: getter/setters */
	private TOTorrent torrent;

	private long	totalSize;

	private int iStartID;

	/** @todo: getter/setters */
	public int iQueueLocation;

	public boolean bSequentialDownload;
	
	/** @todo: getter/setters */
	public boolean isValid;

	/** @todo: getter/setters */
	private boolean bDeleteFileOnCancel;
	private boolean bDeleteFileOnCancelSet;
	
	private TorrentOpenFileOptions[] files = null;

	/** @todo: getter/setters */
	public boolean disableIPFilter = false;

	private Map<Integer, File> initial_linkage_map = null;

	private final CopyOnWriteList<FileListener> fileListeners = new CopyOnWriteList<>(1);

	public Map<String, Boolean> peerSource 		= new HashMap<>();

	private Map<String, Boolean> enabledNetworks = new HashMap<>();

	private List<Tag>	initialTags = new ArrayList<>();

	private List<List<String>>	updatedTrackers;

	private int max_up;
	private int max_down;

	private boolean	hide_errors;

	public static final int CA_NONE			= 0;
	public static final int CA_ACCEPT		= 1;
	public static final int CA_REJECT		= 2;

		// add stuff above here -> update the clone constructor

	private int 		complete_action	= CA_NONE;
	private boolean		dirty;

	/**
	 * Init
	 *
	 * @param sFileName
	 * @param torrent
	 * @param bDeleteFileOnCancel
	 */
	public TorrentOpenOptions(String sFileName, TOTorrent torrent,
			boolean bDeleteFileOnCancel) {
		this();
		this.bDeleteFileOnCancel = bDeleteFileOnCancel;
		bDeleteFileOnCancelSet = true;
		this.sFileName = sFileName;
		this.sOriginatingLocation = sFileName;
		this.setTorrent(torrent);
	}

	public TorrentOpenOptions() {
		iStartID = getDefaultStartMode();
		iQueueLocation = QUEUELOCATION_BOTTOM;
		bSequentialDownload = false;
		isValid = true;
		this.sDestDir = COConfigurationManager.getStringParameter(PARAM_DEFSAVEPATH);

		for (int i = 0; i < AENetworkClassifier.AT_NETWORKS.length; i++) {

			String nn = AENetworkClassifier.AT_NETWORKS[i];

			String config_name = "Network Selection Default." + nn;

			enabledNetworks.put( nn, COConfigurationManager.getBooleanParameter( config_name ));
		}
	}

	/**
	 * clones everything except files and torrent
	 * @param toBeCloned
	 */
	public TorrentOpenOptions(TorrentOpenOptions toBeCloned) {
		this.sOriginatingLocation = toBeCloned.sOriginatingLocation;
		this.sFileName = toBeCloned.sFileName;
		this.sDestDir = toBeCloned.sDestDir;
		this.sDestSubDir = toBeCloned.sDestSubDir;
		this.iStartID = toBeCloned.iStartID;
		this.iQueueLocation = toBeCloned.iQueueLocation;
		this.bSequentialDownload = toBeCloned.bSequentialDownload;
		this.isValid = toBeCloned.isValid;
		this.bDeleteFileOnCancel = toBeCloned.bDeleteFileOnCancel;
		bDeleteFileOnCancelSet = toBeCloned.bDeleteFileOnCancelSet;
		this.disableIPFilter = toBeCloned.disableIPFilter;
		// this.torrent = ... // no clone
		// this.initial_linkage_map = ... // no clone
		// this.files = ... // no clone
		this.peerSource = toBeCloned.peerSource == null ? null : new HashMap<>(toBeCloned.peerSource);
		this.enabledNetworks = toBeCloned.enabledNetworks == null ? null : new HashMap<>(toBeCloned.enabledNetworks);
		this.initialTags = toBeCloned.initialTags == null ? null : new ArrayList<>(toBeCloned.initialTags);

		if ( toBeCloned.updatedTrackers != null ){
			updatedTrackers = new ArrayList<>();
			for (List<String> l: toBeCloned.updatedTrackers){
				updatedTrackers.add(new ArrayList<>(l));
			}
		}
		this.max_up 		= toBeCloned.max_up;
		this.max_down 		= toBeCloned.max_down;
		this.hide_errors	= toBeCloned.hide_errors;
	}

	public static int getDefaultStartMode() {
		return (COConfigurationManager.getBooleanParameter("Default Start Torrents Stopped"))
				? STARTMODE_STOPPED : STARTMODE_QUEUED;
	}

	public File getInitialLinkage(int index) {
		return initial_linkage_map == null ? null : (initial_linkage_map.get(index));
	}

	public String getParentDir() {
		return sDestDir;
	}

	public void setParentDir(String parentDir) {
		sDestDir = parentDir;
		parentDirChanged();
	}

	public void
	setManualRename(
		String	manualRename )
	{
		this.manualRename = manualRename;
	}

	public String
	getManualRename()
	{
		return( manualRename );
	}

	public void
	setDeleteFileOnCancel(
		boolean		b )
	{
		bDeleteFileOnCancel = b;
		bDeleteFileOnCancelSet = true;
	}
	
	public boolean
	getDeleteFileOnCancel()
	{
		return( bDeleteFileOnCancel );
	}
	
	public String
	getSubDir()
	{
		return( sDestSubDir );
	}

	public void
	setExplicitDataDir(
		String		parent_dir,
		String		sub_dir )
	{
		sDestDir 	= parent_dir;
		sDestSubDir	= sub_dir;

		explicitDataDir	= true;

		parentDirChanged();
	}

	public boolean
	isExplicitDataDir()
	{
		return( explicitDataDir );
	}

	public boolean
	isSimpleTorrent()
	{
		return( torrent.isSimpleTorrent());
	}

	public int
	getStartMode()
	{
		return( iStartID );
	}

	public void
	setStartMode(
		int	m )
	{
		iStartID = m;
	}

	public Map<String, Boolean>
	getEnabledNetworks()
	{
		return(new HashMap<>(enabledNetworks));
	}

	public void
	setNetworkEnabled(
		String		net,
		boolean		enabled )
	{
		enabledNetworks.put( net, enabled );
	}

	public String getDataDir() {
		if (torrent.isSimpleTorrent())
			return sDestDir;
		return new File(sDestDir, sDestSubDir == null
				? FileUtil.convertOSSpecificChars(getTorrentName(), true) : sDestSubDir).getPath();
	}

	private String getSmartDestDir() {
		String sSmartDir = sDestDir;
		try {
			String name = getTorrentName();
			String torrentFileName = sFileName == null ? ""
					: new File(sFileName).getName().replaceFirst("\\.torrent$", "");
			int totalSegmentsLengths = 0;

			String[][] segments = {
				name.split("[^a-zA-Z]+"),
				torrentFileName.split("[^a-zA-Z]+")
			};
			List downloadManagers = CoreFactory.getSingleton().getGlobalManager().getDownloadManagers();

			for (int x = 0; x < segments.length; x++) {
				String[] segmentArray = segments[x];
				for (int i = 0; i < segmentArray.length; i++) {
					int l = segmentArray[i].length();
					if (l <= 1) {
						continue;
					}
					segmentArray[i] = segmentArray[i].toLowerCase();
					totalSegmentsLengths += l;
				}
			}

			int maxMatches = 0;
			DownloadManager match = null;
			long scanStarted = SystemTime.getCurrentTime();
			for (Iterator iter = downloadManagers.iterator(); iter.hasNext();) {
				DownloadManager dm = (DownloadManager) iter.next();

				if (dm.getState() == DownloadManager.STATE_ERROR) {
					continue;
				}

				DownloadManagerState dms = dm.getDownloadState();

				if ( 	dms.getFlag( DownloadManagerState.FLAG_LOW_NOISE ) ||
						dms.getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){

					continue;
				}

				int numMatches = 0;

				String dmName = dm.getDisplayName().toLowerCase();

				for (int x = 0; x < segments.length; x++) {
					String[] segmentArray = segments[x];
					for (int i = 0; i < segmentArray.length; i++) {
						int l = segmentArray[i].length();
						if (l <= 1) {
							continue;
						}

						String segment = segmentArray[i];

						if (dmName.contains(segment)) {
							numMatches += l;
						}
					}
				}

				if (numMatches > maxMatches) {
					maxMatches = numMatches;
					match = dm;
				}

				long scanTime = SystemTime.getCurrentTime() - scanStarted;
				if (match != null && scanTime > 500) {
					break;
				}
				if (match == null && scanTime > 1000) {
					break;
				}
			}
			if (match != null) {
				//System.out.println(match + ": " + (maxMatches * 100 / totalSegmentsLengths) + "%\n");
				int iMatchLevel = (maxMatches * 100 / totalSegmentsLengths);
				if (iMatchLevel >= 30) {
					File f = match.getSaveLocation();
					if (!f.isDirectory() || match.getDiskManagerFileInfo().length > 1) {
						// don't place data within another torrent's data dir
						f = f.getParentFile();
					}

					if (f != null && f.isDirectory()) {
						sSmartDir = f.getAbsolutePath();
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		if (sSmartDir.length() == 0) {
			try {
				return ConfigurationDefaults.getInstance().getStringParameter(PARAM_DEFSAVEPATH);
			} catch (ConfigurationParameterNotFoundException e) {
			}
		}
		return sSmartDir;
	}

	public List<Tag>
	getInitialTags()
	{
		return(new ArrayList<>(initialTags));
	}

	public void
	setInitialTags(
		List<Tag>		tags )
	{
		initialTags = tags;
	}

	public void
	setDirty()
	{
		dirty = true;
	}

	public boolean
	getAndClearDirt()
	{
		boolean	result = dirty;

		dirty = false;

		return( result );
	}

	public List<List<String>>
	getTrackers(
		boolean	if_updated )
	{
		if ( updatedTrackers != null ){

			return( updatedTrackers );
		}

		if ( if_updated ){

			return( null );
		}

		if ( torrent == null ){

			return(new ArrayList<>(0));

		}else{

			return( TorrentUtils.announceGroupsToList(torrent));
		}
	}

	public void
	setTrackers(
		List<List<String>>	trackers )
	{
		updatedTrackers = trackers;
	}

	public void
	setMaxUploadSpeed(
		int		kbs )
	{
		max_up	= kbs;
	}

	public int
	getMaxUploadSpeed()
	{
		return( max_up );
	}

	public void
	setMaxDownloadSpeed(
		int		kbs )
	{
		max_down	= kbs;
	}

	public int
	getMaxDownloadSpeed()
	{
		return( max_down );
	}
	public void
	setHideErrors(
		boolean		h )
	{
		hide_errors	= h;
	}

	public boolean
	getHideErrors()
	{
		return( hide_errors );
	}

	public TorrentOpenFileOptions[] getFiles() {
		if (files == null && torrent != null) {
			TOTorrentFile[] tfiles = torrent.getFiles();
			files = new TorrentOpenFileOptions[tfiles.length];

			Set<String>	skip_extensons = TorrentUtils.getSkipExtensionsSet();

			long	skip_min_size = COConfigurationManager.getLongParameter( "File.Torrent.AutoSkipMinSizeKB" )*1024L;

			for (int i = 0; i < files.length; i++) {
				TOTorrentFile	torrentFile = tfiles[i];

				String 	orgFullName = torrentFile.getRelativePath(); // translated to locale
				String	orgFileName = new File(orgFullName).getName();

				boolean	wanted = true;

				if ( skip_min_size > 0 && torrentFile.getLength() < skip_min_size ){

					wanted = false;

				}else if ( skip_extensons.size() > 0 ){

					int	pos = orgFileName.lastIndexOf( '.' );

					if ( pos != -1 ){

						String	ext = orgFileName.substring( pos+1 );

						wanted = !skip_extensons.contains( ext );
					}
				}

				files[i] = new TorrentOpenFileOptions( this, i, orgFullName, orgFileName, torrentFile.getLength(), wanted );
			}
		}

		return files;
	}

	public long
	getTotalSize()
	{
		if ( totalSize == 0 ){

			TorrentOpenFileOptions[] files = getFiles();

			if ( files != null ){

				for ( TorrentOpenFileOptions file: files ){

					totalSize += file.lSize;
				}
			}
		}

		return( totalSize );
	}
	public String getTorrentName() {
		return TorrentUtils.getLocalisedName(torrent);
	}

	public boolean allFilesMoving() {
		TorrentOpenFileOptions[] files = getFiles();
		for (int j = 0; j < files.length; j++) {
			if (files[j].isLinked()) {
				return false;
			}
		}
		return true;
	}

	public boolean allFilesExist() {
		// check if all selected files exist
		TorrentOpenFileOptions[] files = getFiles();
		for (int i = 0; i < files.length; i++) {
			TorrentOpenFileOptions fileInfo = files[i];
			if (!fileInfo.isToDownload())
				continue;

			File file = fileInfo.getDestFileFullName();
			if (!file.exists() || file.length() != fileInfo.lSize) {
				return false;
			}
		}
		return true;
	}

	public void renameDuplicates() {
		if (iStartID == STARTMODE_SEEDING
				|| !COConfigurationManager.getBooleanParameter("DefaultDir.AutoSave.AutoRename")
				|| allFilesExist()) {
			return;
		}

		if (!torrent.isSimpleTorrent()) {
			if (new File(getDataDir()).isDirectory()) {
				File f;
				int idx = 0;
				do {
					idx++;
					f = new File(getDataDir() + "-" + idx);
				} while (f.isDirectory());

				sDestSubDir = f.getName();
			}
		} else {
			// should only be one file
			TorrentOpenFileOptions[] fileInfos = getFiles();
			for (int i = 0; i < fileInfos.length; i++) {
				TorrentOpenFileOptions info = fileInfos[i];

				File file = info.getDestFileFullName();
				int idx = 0;
				while (file.exists()) {
					idx++;
					file = new File(info.getDestPathName(), idx + "-"
							+ info.getDestFileName());
				}

				info.setDestFileName(file.getName(),false);
			}
		}
	}

	/*
	private Boolean has_multiple_small_files = null;
	private boolean hasMultipleSmallFiles() {
		TorrentFileInfo[] tfi_files = getFiles();
		if (tfi_files.length <= MAX_NODOWNLOAD_COUNT)
			return false;

		int small_files_counted = 0;
		for (int i=0; i<tfi_files.length; i++) {
			if (tfi_files[i].lSize < MIN_NODOWNLOAD_SIZE) {
				small_files_counted++;
				if (small_files_counted > MAX_NODOWNLOAD_COUNT) {
					return true;
				}
			}
		}

		return false;
	}
	*/

	// Indicates whether all files in this torrent can be deselected
	// (if not, then it occurs on a per-file basis).
	public boolean okToDisableAll() {
		return true;

		/*
		if (iStartID == STARTMODE_SEEDING)
			return true;

		// Do we have multiple small files? We'll allow all of them to
		// be disabled if we do.
		if (has_multiple_small_files == null) {
			has_multiple_small_files = new Boolean(hasMultipleSmallFiles());
		}

		// You can disable all files if there are lots of small files.
		return has_multiple_small_files.booleanValue();
		*/
	}

	public TOTorrent getTorrent() {
		return torrent;
	}

	public void setTorrent(TOTorrent torrent) {
		this.torrent = torrent;

		if (COConfigurationManager.getBooleanParameter("DefaultDir.BestGuess") &&
				!COConfigurationManager.getBooleanParameter(PARAM_MOVEWHENDONE)) {

			this.sDestDir = getSmartDestDir();
		}

		if (torrent == null) {
			initial_linkage_map = null;
		} else {
			initial_linkage_map = TorrentUtils.getInitialLinkage(torrent);

			// Force a check on the encoding, will prompt user if we dunno
			try {
				LocaleTorrentUtil.getTorrentEncoding(torrent);
			} catch (Exception e) {
				e.printStackTrace();
			}

			Set<String> tracker_hosts = TorrentUtils.getUniqueTrackerHosts( torrent );

			final Set<String>	networks = new HashSet<>();

			boolean	decentralised = false;

			for ( String host: tracker_hosts ){

				if ( TorrentUtils.isDecentralised( host )){

					decentralised = true;

				}else{

					String network = AENetworkClassifier.categoriseAddress( host );

					networks.add( network );
				}
			}

			List<String> network_cache = TorrentUtils.getNetworkCache( torrent );

			if ( network_cache.size() > 0 ){

					// If the network cache doesn't have some networks enabled then we propagate this
					// onto the defaults for this torrent. Use case: user has I2P only magnet download but
					// the resulting torrent file happens to have public trackers - don't surprise the user
					// by leaving 'Public' enabled

				for ( String net: enabledNetworks.keySet()){

					boolean enabled = network_cache.contains( net );

					if ( !enabled ){

						enabledNetworks.put( net, false );
					}
				}
			}

			networks.addAll( network_cache );

				// could do something here if multiple networks to get user to decide what to do...

			boolean	enable_i2p = networks.contains( AENetworkClassifier.AT_I2P );
			String enable_i2p_reason = null;

			if ( enable_i2p ){

				enable_i2p_reason = MessageText.getString("azneti2phelper.install.reason.i2ptracker");

			} else {

					// if torrent is purely decentralised then we don't really know what network so enable it

				if ( tracker_hosts.size() == 1 && decentralised ){

					// 2015/02/27 - holding off on this for the moment as unsure of the number of purely-dht
					// torrents out there (e.g. old VHDN content is purely-dht...)

					//enable_i2p_reason = MessageText.getString("azneti2phelper.install.reason.decentralised");
					//enable_i2p = true;
				}
			}

			if ( enabledNetworks.get(AENetworkClassifier.AT_I2P)){

				// case where use chooses I2P network as default.  We want to prompt for plugin install

				enable_i2p = true;
			}

			if ( enable_i2p ){

				String[]	providers = { "azneti2p", "azneti2phelper" };

				boolean	found = false;

				for ( String provider: providers ){

					if ( CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( provider ) != null ){

						found = true;

						break;
					}
				}

				if ( found ){

					enabledNetworks.put( AENetworkClassifier.AT_I2P, true );

						// disable public if purely i2p

					if ( networks.contains( AENetworkClassifier.AT_I2P ) && networks.size() == 1 ){

						enabledNetworks.put( AENetworkClassifier.AT_PUBLIC, false );
					}
				}else{

					final boolean[]	install_outcome = { false };

					if ( I2PHelpers.installI2PHelper(
							enable_i2p_reason,
							"azneti2phelper.install.open.torrent",
							install_outcome,
							new Runnable()
							{
								@Override
								public void
								run()
								{
									if ( !install_outcome[0] ){

										// could try and revert settings but can't
										// be bothered atm as it needs additional stuff to
										// update the UI check boxes...

									}
								}
							})){

							// here installation has at least started so assume it'll complete

						enabledNetworks.put( AENetworkClassifier.AT_I2P, true );

							// disable public if purely i2p

						if ( networks.contains( AENetworkClassifier.AT_I2P ) && networks.size() == 1 ){

							enabledNetworks.put( AENetworkClassifier.AT_PUBLIC, false );
						}
					}
				}
			}

			boolean	enable_tor = networks.contains( AENetworkClassifier.AT_TOR );

			if ( enable_tor ){

				String[]	providers = { "aznettor" };

				boolean	found = false;

				for ( String provider: providers ){

					if ( CoreFactory.getSingleton().getPluginManager().getPluginInterfaceByID( provider ) != null ){

						found = true;

						break;
					}
				}

				if ( found ){

					enabledNetworks.put( AENetworkClassifier.AT_TOR, true );

						// disable public if not selected

					if ( !networks.contains( AENetworkClassifier.AT_PUBLIC )){

						enabledNetworks.put( AENetworkClassifier.AT_PUBLIC, false );
					}
				}
			}

			renameDuplicates();
		}
	}


	public void addListener(FileListener l) {
		fileListeners.add(l);
	}

	public void removeListener(FileListener l) {
		fileListeners.remove(l);
	}

	public interface FileListener {
		public void toDownloadChanged(TorrentOpenFileOptions torrentOpenFileOptions, boolean toDownload);
		public void priorityChanged(TorrentOpenFileOptions torrentOpenFileOptions, int priority );
		public void parentDirChanged();
	}

	public void fileDownloadStateChanged(
			TorrentOpenFileOptions torrentOpenFileOptions, boolean toDownload)
	{
		for ( FileListener l : fileListeners) {
			try{
				l.toDownloadChanged(torrentOpenFileOptions, toDownload);
			}catch( Throwable e ){
				Debug.out( e );
			}
		}
	}

	public void filePriorityStateChanged(
			TorrentOpenFileOptions torrentOpenFileOptions, int priority)
	{
		for ( FileListener l : fileListeners) {
			try{
				l.priorityChanged(torrentOpenFileOptions, priority);
			}catch( Throwable e ){
				Debug.out( e );
			}
		}
	}

	public void parentDirChanged()
	{
		for ( FileListener l : fileListeners) {
			try{
				l.parentDirChanged();
			}catch( Throwable e ){
				Debug.out( e );
			}
		}
	}

	public void
	setCompleteAction(
		int		ca )
	{
			// indication of whether options are to be accepted or rejected

		complete_action = ca;
	}

	public int
	getCompleteAction()
	{
		return( complete_action );
	}
	
	public void
	cancel()
	{
		if ( bDeleteFileOnCancel || !bDeleteFileOnCancelSet ){
			
			if ( sFileName != null ){
			
				try{
					File torrentFile = new File(sFileName);
				
					if ( bDeleteFileOnCancel ){
						
						torrentFile.delete();
						
					}else{
						
							// if no explicit instructions then only delete if in configured save directory
						
						if ( COConfigurationManager.getBooleanParameter("Save Torrent Files")) {
					
							String save_dir = COConfigurationManager.getDirectoryParameter("General_sDefaultTorrent_Directory");
							
							if ( torrentFile.getParentFile().getAbsolutePath().equals( new File(save_dir).getAbsolutePath())){
								
								torrentFile.delete();
							}
						}
					}
				}catch( Throwable e ){
				}
			}
		}
	}
}