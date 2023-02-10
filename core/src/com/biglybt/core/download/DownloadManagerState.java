/*
 * Created on 15-Nov-2004
 * Created by Paul Gardner
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

package com.biglybt.core.download;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.biglybt.core.category.Category;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.IndentWriter;
import com.biglybt.core.util.LinkFileMap;
import com.biglybt.pif.download.Download;

/**
 * @author parg
 */

public interface
DownloadManagerState
{
	public static final long   FAS_DEFAULT					= 0;
	public static final long   FAS_ZERO_NEW					= 1;
	public static final long   FAS_ZERO_NEW_STOP			= 2;
	
	public static final String AT_VERSION					= "version";
	public static final String AT_CATEGORY					= "category";
	public static final String AT_NETWORKS					= "networks";
	public static final String AT_USER						= "user";
	public static final String AT_PEER_SOURCES				= "peersources";
	public static final String AT_PEER_SOURCES_DENIED		= "peersourcesdenied";
	public static final String AT_TRACKER_CLIENT_EXTENSIONS	= "trackerclientextensions";
	public static final String AT_FILE_LINKS_DEPRECATED		= "filelinks";
	public static final String AT_FILE_LINKS2				= "filelinks2";
	public static final String AT_FILE_ALLOC_REQUEST		= "allocreq";		// Map
	public static final String AT_FILE_STORE_TYPES			= "storetypes";
	public static final String AT_FILE_DOWNLOADED			= "filedownloaded";
	public static final String AT_FLAGS						= "flags";
	public static final String AT_PARAMETERS				= "parameters";
	public static final String AT_DISPLAY_NAME              = "displayname";
	public static final String AT_USER_COMMENT              = "comment";
	public static final String AT_RELATIVE_SAVE_PATH        = "relativepath";
	public static final String AT_SECRETS				 	= "secrets";
	public static final String AT_RESUME_STATE		 		= "resumecomplete";
	public static final String AT_PRIMARY_FILE		 		= "primaryfile";
	public static final String AT_PRIMARY_FILE_IDX		 	= "primaryfileidx";
	public static final String AT_TIME_SINCE_DOWNLOAD		= "timesincedl";
	public static final String AT_TIME_SINCE_UPLOAD			= "timesinceul";
	public static final String AT_AVAIL_BAD_TIME			= "badavail";
	public static final String AT_TIME_STOPPED				= "timestopped";
	public static final String AT_INCOMP_FILE_SUFFIX		= "incompfilesuffix";
	public static final String AT_SCRAPE_CACHE				= "scrapecache";	// long value, seeds in upper word, leechers in lower
	public static final String AT_SCRAPE_CACHE_SOURCE		= "scsrc";			// int value - 0=tracker; 1=subscription etc
	public static final String AT_REORDER_MIN_MB			= "reordermb";
	public static final String AT_MD_INFO_DICT_SIZE			= "mdinfodictsize";
	public static final String AT_FILE_OTHER_HASHES			= "fileotherhashes";
	public static final String AT_CANONICAL_SD_DMAP			= "canosavedir";
	public static final String AT_DND_SUBFOLDER				= "dnd_sf";
	public static final String AT_PEAK_RECEIVE_RATE			= "pkdo";
	public static final String AT_PEAK_SEND_RATE			= "pkup";
	public static final String AT_DL_FILE_ALERTS			= "df_alerts";
	public static final String AT_SHARE_RATIO_PROGRESS		= "sr.prog";		// long: left word - timestamp in secs, right word sr in 1000ths
	public static final String AT_FILES_EXPANDED			= "file.expand";	// boolean
	public static final String AT_MERGED_DATA				= "mergedata";		// long
	public static final String AT_DND_PREFIX				= "dnd_pfx";		// string
	public static final String AT_AGGREGATE_SCRAPE_CACHE	= "agsc";			// string <update_time_mins>,<seeds>,<leechers>
	public static final String AT_COMPLETE_LAST_TIME		= "complt";			// long - last time download reported complete, -1 if reported incomplete, 0 if unknown
	public static final String AT_LAST_ADDED_TO_ACTIVE_TAG	= "last.act.tag";	// long - last time added to active tag
	public static final String AT_MOVE_ON_COMPLETE_DIR		= "moc.dir";		// String - explicit move-on-complete folder
	public static final String AT_FILE_FLAGS				= "ff";
	public static final String AT_FILE_ALLOC_STRATEGY		= "fas";			// long
	public static final String AT_TRACKER_SESSION_STATS		= "tss";			// Map
	public static final String AT_TORRENT_SAVE_TIME			= "tst";			// long
	public static final String AT_TORRENT_EXPORT_PROPAGATED	= "tep";			// bool
	public static final String AT_SWARM_TAGS				= "stag";			// list
	public static final String AT_MASK_DL_COMP_OPTIONAL		= "mdlc";			// Boolean (optional)
	public static final String AT_REAL_DM_MAGNET_TIME		= "rdmmt";			// long
	
	public static final String AT_TRANSIENT_FLAGS			= "t_flags";
	public static final String AT_TRANSIENT_TAG_SORT		= "t_tagsort";

	
	public static Object[][] ATTRIBUTE_DEFAULTS = {
		{ AT_VERSION,								new Integer( -1 )},
		{ AT_TIME_SINCE_DOWNLOAD,					new Integer( -1 )},
		{ AT_TIME_SINCE_UPLOAD,						new Integer( -1 )},
		{ AT_AVAIL_BAD_TIME,						new Long( -1 )},
		{ AT_SCRAPE_CACHE,							new Long( -1 )},
		{ AT_SCRAPE_CACHE_SOURCE,					new Integer( 0 )},
		{ AT_REORDER_MIN_MB,						new Integer( -1 )},
		{ AT_SHARE_RATIO_PROGRESS,					new Long( 0 )},
		{ AT_FILE_ALLOC_STRATEGY,					new Long( FAS_DEFAULT )},
	};

	public static final long FLAG_ONLY_EVER_SEEDED						= Download.FLAG_ONLY_EVER_SEEDED;
	public static final long FLAG_SCAN_INCOMPLETE_PIECES				= Download.FLAG_SCAN_INCOMPLETE_PIECES;
	public static final long FLAG_DISABLE_AUTO_FILE_MOVE    			= Download.FLAG_DISABLE_AUTO_FILE_MOVE;
	public static final long FLAG_MOVE_ON_COMPLETION_DONE   			= Download.FLAG_MOVE_ON_COMPLETION_DONE;
	public static final long FLAG_LOW_NOISE								= Download.FLAG_LOW_NOISE;
	public static final long FLAG_ALLOW_PERMITTED_PEER_SOURCE_CHANGES	= Download.FLAG_ALLOW_PERMITTED_PEER_SOURCE_CHANGES;
	public static final long FLAG_DO_NOT_DELETE_DATA_ON_REMOVE  		= Download.FLAG_DO_NOT_DELETE_DATA_ON_REMOVE;
	public static final long FLAG_FORCE_DIRECT_DELETE			  		= Download.FLAG_FORCE_DIRECT_DELETE;
	public static final long FLAG_DISABLE_IP_FILTER				  		= Download.FLAG_DISABLE_IP_FILTER;
	public static final long FLAG_METADATA_DOWNLOAD				  		= Download.FLAG_METADATA_DOWNLOAD;
	public static final long FLAG_ERROR_REPORTED				  		= Download.FLAG_ERROR_REPORTED;
	public static final long FLAG_INITIAL_NETWORKS_SET					= Download.FLAG_INITIAL_NETWORKS_SET;
	public static final long FLAG_SEQUENTIAL_DOWNLOAD					= Download.FLAG_SEQUENTIAL_DOWNLOAD;
	public static final long FLAG_DISABLE_STOP_AFTER_ALLOC				= Download.FLAG_DISABLE_STOP_AFTER_ALLOC;

	
	
	public static final int	FILE_FLAG_NOT_NEW		= 0x00000001;
	
	
	public static final String	PARAM_MAX_PEERS							= "max.peers";
	public static final String	PARAM_MAX_PEERS_WHEN_SEEDING			= "max.peers.when.seeding";
	public static final String	PARAM_MAX_PEERS_WHEN_SEEDING_ENABLED	= "max.peers.when.seeding.enabled";
	public static final String	PARAM_MAX_SEEDS							= "max.seeds";
	public static final String	PARAM_MAX_UPLOADS						= "max.uploads";
	public static final String	PARAM_MAX_UPLOADS_WHEN_SEEDING			= "max.uploads.when.seeding";
	public static final String	PARAM_MAX_UPLOADS_WHEN_SEEDING_ENABLED	= "max.uploads.when.seeding.enabled";
	public static final String	PARAM_STATS_COUNTED						= "stats.counted";
	public static final String	PARAM_DOWNLOAD_ADDED_TIME				= "stats.download.added.time";
	public static final String	PARAM_DOWNLOAD_COMPLETED_TIME			= "stats.download.completed.time";
	public static final String	PARAM_DOWNLOAD_FILE_COMPLETED_TIME		= "stats.download.file.completed.time";
	public static final String	PARAM_DOWNLOAD_LAST_ACTIVE_TIME			= "stats.download.last.active.time";
	public static final String	PARAM_MAX_UPLOAD_WHEN_BUSY				= "max.upload.when.busy";
	public static final String  PARAM_DND_FLAGS							= "dndflags";
	public static final String  PARAM_RANDOM_SEED						= "rand";
	public static final String	PARAM_UPLOAD_PRIORITY					= "up.pri";
	public static final String	PARAM_MIN_SHARE_RATIO					= "sr.min";		// in thousandths - 1000 = sr of 1.0
	public static final String	PARAM_MAX_SHARE_RATIO					= "sr.max";		// in thousandths - 1000 = sr of 1.0

	public static final int DEFAULT_MAX_UPLOADS		= 4;
	public static final int MIN_MAX_UPLOADS			= 2;
	public static final int DEFAULT_UPLOAD_PRIORITY	= 0;

	public static Object[][] PARAMETERS = {
		{ PARAM_MAX_PEERS,							new Integer( 0 ) },
		{ PARAM_MAX_PEERS_WHEN_SEEDING,				new Integer( 0 ) },
		{ PARAM_MAX_PEERS_WHEN_SEEDING_ENABLED, Boolean.FALSE},
		{ PARAM_MAX_SEEDS,							new Integer( 0 ) },
		{ PARAM_MAX_UPLOADS,						new Long( DEFAULT_MAX_UPLOADS ) },
		{ PARAM_MAX_UPLOADS_WHEN_SEEDING, 			new Integer( DEFAULT_MAX_UPLOADS ) },
		{ PARAM_MAX_UPLOADS_WHEN_SEEDING_ENABLED, Boolean.FALSE},
		{ PARAM_STATS_COUNTED, Boolean.FALSE},
		{ PARAM_DOWNLOAD_ADDED_TIME,				new Long( 0 ) },
		{ PARAM_DOWNLOAD_FILE_COMPLETED_TIME, 		new Long( 0 ) },
		{ PARAM_DOWNLOAD_COMPLETED_TIME, 			new Long( 0 ) },
		{ PARAM_DOWNLOAD_LAST_ACTIVE_TIME, new Long( 0 ) },
		{ PARAM_MAX_UPLOAD_WHEN_BUSY,				new Long( 0 ) },
		{ PARAM_DND_FLAGS, 							new Long( 0 ) },
		{ PARAM_RANDOM_SEED, 						new Long( 0 ) },
		{ PARAM_UPLOAD_PRIORITY, 					new Integer( DEFAULT_UPLOAD_PRIORITY ) },
		{ PARAM_MIN_SHARE_RATIO, 					new Integer( 0 ) },
		{ PARAM_MAX_SHARE_RATIO, 					new Integer( 0 ) },
	};

	public static final int	TRANSIENT_FLAG_FRIEND_FP	= 0x00000001;
	public static final int	TRANSIENT_FLAG_TAG_FP		= 0x00000002;
	
			
	public TOTorrent
	getTorrent();

	public DownloadManager
	getDownloadManager();

	public File
	getStateFile( );

	public boolean
	getAndClearRecoveredStatus();
	
	public void
	setFlag(
		long		flag,
		boolean		set );

	public boolean
	getFlag(
		long		flag );

	public long
	getFlags();

	public void
	setTransientFlag(
		long		flag,
		boolean		set );

	public boolean
	getTransientFlag(
		long		flag );

	public long
	getTransientFlags();
	
	public Object
	getTransientAttribute(
		String		name );		// use AT_TRANSIENT_X names
	
	public void
	setTransientAttribute(
		String		name,
		Object		value );
	
		/**
		 * Reset to default value
		 * @param name
		 */

	public void
	setParameterDefault(
		String	name );

	public int
	getIntParameter(
		String	name );

	public void
	setIntParameter(
		String	name,
		int		value );

	public long
	getLongParameter(
		String	name );

	public void
	setLongParameter(
		String	name,
		long	value );

	public boolean
	getBooleanParameter(
		String	name );

	public void
	setBooleanParameter(
		String		name,
		boolean		value );

	public void
	clearResumeData();

	public Map
	getResumeData();

	public void
	setResumeData(
		Map	data );

	public boolean
	isResumeDataComplete();

	/**
	 * Ordered by time, most recent last
	 * @return
	 */
	
	public List<ResumeHistory>
	getResumeDataHistory();
	
	public void
	restoreResumeData(
		ResumeHistory	history );
	
	public void
	clearTrackerResponseCache();

	public Map
	getTrackerResponseCache();

	public void
	setTrackerResponseCache(
		Map		value );

	public Category
	getCategory();

	public void
	setCategory(
		Category cat );

	public String getDisplayName();
	public void setDisplayName(String name);

	public String getUserComment();
	public void setUserComment(String name);

	public String getRelativeSavePath();

	public void setPrimaryFile(DiskManagerFileInfo dmfi);
	public DiskManagerFileInfo getPrimaryFile();

	public String
	getTrackerClientExtensions();

	public void
	setTrackerClientExtensions(
		String		value );

	public String[]		// from AENetworkClassifier constants
	getNetworks();

	public boolean
	isNetworkEnabled(
	    String		network); //from AENetworkClassifier constants

	public void
	setNetworks(
		String[]	networks );	// from AENetworkClassifier constants

	public void
	setNetworkEnabled(
	    String		network,				// from AENetworkClassifier constants
	    boolean		enabled);

	public String[]		// from PEPeerSource constants
	getPeerSources();

	public boolean
	isPeerSourcePermitted(
		String		peerSource );

	public void
	setPeerSourcePermitted(
		String		peerSource,
		boolean		permitted );

	public boolean
	isPeerSourceEnabled(
	    String		peerSource); // from PEPeerSource constants

	public void
	setPeerSources(
		String[]	sources );	// from PEPeerSource constants

	public void
	setPeerSourceEnabled(
	    String		source,		// from PEPeerSource constants
	    boolean		enabled);

		// file links

	public void
	setFileLink(
		int		source_index,
		File	link_source,
		File	link_destination );

	public void
	setFileLinks(
		List<Integer>	source_indexes,
		List<File>		link_sources,
		List<File>		link_destinations );

	public void
	clearFileLinks();

	public File
	getFileLink(
		int		source_index,
		File	link_source );

		/**
		 * returns a File -> File map of the defined links (empty if no links)
		 * @return
		 */

	public LinkFileMap
	getFileLinks();

	public int
	getFileFlags(
		int		file_index );
	
	public void
	setFileFlags(
		int		file_index,
		int		flags );
	
	/**
	 * @return
	 */
	boolean isOurContent();

	// General access - make sure you use an AT_ value defined above when calling
	// these methods.
	
	public void setAttribute(String	name, String value);
	public void setAttribute(String	name, String value, boolean setDirty);
	public String getAttribute(String name);
	public void	setMapAttribute(String name, Map value);
	public Map getMapAttribute(String name);
	public void	setListAttribute(String	name, String[] values);
	public String[]	getListAttribute(String	name);
	public String getListAttribute(String name, int idx);
	public void setIntAttribute(String name, int value);
	public int getIntAttribute(String name);
	public void setLongAttribute(String name, long value);
	public long getLongAttribute(String name);
	public void setBooleanAttribute(String name, boolean value);
	public boolean getBooleanAttribute(String name);
	public boolean hasAttribute(String name);
	public void removeAttribute(String name);

	public default Boolean
	getOptionalBooleanAttribute(
		String		name )
	{
		if ( hasAttribute(name)){
			return( getBooleanAttribute(name));
		}else{
			return( null );
		}
	}
	
	public default void
	setOptionalBooleanAttribute(
		String		name,
		Boolean		value )
	{
		if ( value == null ){
			removeAttribute( name );
		}else{
			setBooleanAttribute(name, value );
		}
	}

	
	public void
	setActive(
		boolean	active );

	public void discardFluff();

	public void
	save( boolean interim );

	public boolean
	exportState(
		File	target_dir );

		/**
		 * deletes the saved state
		 */

	public void
	delete();

	/**
	 * @param name
	 * @return
	 */
	boolean parameterExists(String name);

	public void generateEvidence(IndentWriter writer);

	public void dump( IndentWriter writer );

	/**
	 * This method should only be invoked in matching try-finally pairs. If it is invoked with true
	 * multiple times it must be invoked with false the equal amount of times to reallow state
	 * writes
	 *
	 * @param suppress
	 *            when set to true prevents flushing of the state/increments the internal nesting
	 *            counter, decrements/allows flush otherwise
	 */
	public void suppressStateSave(boolean suppress);

	public void addListener(DownloadManagerStateAttributeListener l, String attribute, int event_type);
	public void removeListener(DownloadManagerStateAttributeListener l, String attribute, int event_type);
	
	public interface
	ResumeHistory
	{
		public long 
		getDate();
	}
}
