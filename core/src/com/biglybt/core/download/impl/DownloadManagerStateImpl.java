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

package com.biglybt.core.download.impl;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.category.Category;
import com.biglybt.core.category.CategoryManager;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFactory;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.*;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.LogRelation;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peer.PEPeerSource;
import com.biglybt.core.torrent.*;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.util.*;

/**
 * @author parg
 * Overall aim of this is to stop updating the torrent file itself and update something
 * the client owns. To this end a file based on torrent hash is created in user-dir/active
 * It is actually just a copy of the torrent file
 */

public class
DownloadManagerStateImpl
	implements DownloadManagerState, ParameterListener
{
	private static final int	VER_INCOMING_PEER_SOURCE	= 1;
	private static final int	VER_HOLE_PUNCH_PEER_SOURCE	= 2;
	private static final int	VER_CURRENT					= VER_HOLE_PUNCH_PEER_SOURCE;


	private static final LogIDs LOGID = LogIDs.DISK;
	private static final String			RESUME_KEY						= "resume";
	private static final String			RESUME_HISTORY_KEY				= "resume_history";
	private static final String			TRACKER_CACHE_KEY				= "tracker_cache";
	private static final String			ATTRIBUTE_KEY					= "attributes";
	private static final String			AZUREUS_PROPERTIES_KEY			= "azureus_properties";
	private static final String			AZUREUS_PRIVATE_PROPERTIES_KEY	= "azureus_private_properties";

	private static final File			ACTIVE_DIR;

	public static boolean SUPPRESS_FIXUP_ERRORS = false;

	static{

		ACTIVE_DIR = FileUtil.getUserFile( "active" );

		if ( !ACTIVE_DIR.exists()){

			FileUtil.mkdirs(ACTIVE_DIR);
		}
	}
	
	private static boolean disable_interim_saves;
	// private static LoggerChannel 	save_log;
	
	
	static{
		COConfigurationManager.addAndFireParameterListener(
			ConfigKeys.File.BCFG_DISABLE_SAVE_INTERIM_DOWNLOAD_STATE,
			(n)->{
				disable_interim_saves = COConfigurationManager.getBooleanParameter( n );
				
				/*
				if ( disable_interim_saves && Constants.isCVSVersion() && save_log == null ){
					
					save_log = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getLogger().getChannel( "DownloadStateSaves" );

					save_log.setDiagnostic();

					save_log.setForce( true );
				}
				*/
			});
	}

	private static final Random	random = RandomUtils.SECURE_RANDOM;

	private static final Map	default_parameters;
	private static final Map	default_attributes;

	static{
		default_parameters  = new HashMap();

		for (int i=0;i<PARAMETERS.length;i++){

			default_parameters.put( PARAMETERS[i][0], PARAMETERS[i][1] );
		}

		default_attributes  = new HashMap();

		for (int i=0;i<ATTRIBUTE_DEFAULTS.length;i++){

			default_attributes.put( ATTRIBUTE_DEFAULTS[i][0], ATTRIBUTE_DEFAULTS[i][1] );
		}

			// only add keys that will point to Map objects here!
		
		TorrentUtils.registerMapFluff( new String[]{ TRACKER_CACHE_KEY, RESUME_KEY, RESUME_HISTORY_KEY });
	}
	
	private static boolean debug_on;
	
	public static void
	setDebugOn(
		boolean		on )
	{
		debug_on = on;
	}

	private static Object
	getDefaultOverride(
		String		name,
		Object		value )
	{
			// default overrides
	
			// **** note - if you add to these make sure you extend the parameter listeners
			// registered as well (see static initialiser at top)
	
		if ( name == PARAM_MAX_UPLOADS_WHEN_SEEDING_ENABLED ){
	
			if ( COConfigurationManager.getBooleanParameter( "enable.seedingonly.maxuploads" )){
	
				value = Boolean.TRUE;
			}
	
		}else if ( name == PARAM_MAX_UPLOADS_WHEN_SEEDING ){
	
			int	def = COConfigurationManager.getIntParameter( "Max Uploads Seeding" );
	
			value = new Integer( def );
	
		}else if ( name == PARAM_MAX_UPLOADS ){
	
			int	def = COConfigurationManager.getIntParameter("Max Uploads" );
	
			value = new Integer( def );
	
		}else if ( name == PARAM_MAX_PEERS ){
	
			int	def = COConfigurationManager.getIntParameter( "Max.Peer.Connections.Per.Torrent" );
	
			value = new Integer( def );
	
		}else if ( name == PARAM_MAX_PEERS_WHEN_SEEDING_ENABLED ){
	
			if ( COConfigurationManager.getBooleanParameter( "Max.Peer.Connections.Per.Torrent.When.Seeding.Enable" )){
	
				value = Boolean.TRUE;
			}
	
		}else if ( name == PARAM_MAX_PEERS_WHEN_SEEDING ){
	
			int	def = COConfigurationManager.getIntParameter( "Max.Peer.Connections.Per.Torrent.When.Seeding" );
	
			value = new Integer( def );
	
		}else if ( name == PARAM_MAX_SEEDS ){
	
			value = new Integer(COConfigurationManager.getIntParameter( "Max Seeds Per Torrent" ));
	
		}else if ( name == PARAM_RANDOM_SEED ){
	
			long	rand = random.nextLong();
		
			value = new Long( rand );
		}
		
		return( value );
	}
	
	public static Integer
	getIntParameterDefault(
		String	name )
	{
		Object value = default_parameters.get( name );
		
		if ( value != null ){
			
			value = getDefaultOverride( name, value );
		}
		
		return( value instanceof Number?((Number)value).intValue():null );
	}
	
	public static Boolean
	getBooleanParameterDefault(
		String	name )
	{
		Object value = default_parameters.get( name );
		
		if ( value != null ){
			
			value = getDefaultOverride( name, value );
		}
		
		return( value instanceof Boolean?(Boolean)value: null );
	}
	
	private static final AEMonitor	class_mon	= new AEMonitor( "DownloadManagerState:class" );

	static final Map<HashWrapper,DownloadManagerStateImpl>		state_map 					= new HashMap<>();

	static{
		ParameterListener listener =
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameterName)
				{
					List<DownloadManagerStateImpl> states;

					synchronized( state_map ){

						states = new ArrayList<>(state_map.values());
					}

					for ( DownloadManagerStateImpl state: states ){

						try{
							state.parameterChanged( parameterName );

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			};

		COConfigurationManager.addParameterListener( "Max.Peer.Connections.Per.Torrent.When.Seeding", listener );
		COConfigurationManager.addParameterListener( "Max.Peer.Connections.Per.Torrent.When.Seeding.Enable", listener );
		COConfigurationManager.addParameterListener( "Max.Peer.Connections.Per.Torrent", listener );
		COConfigurationManager.addParameterListener( "Max Uploads", listener );
		COConfigurationManager.addParameterListener( "Max Uploads Seeding", listener );
		COConfigurationManager.addParameterListener( "Max Seeds Per Torrent", listener );
		COConfigurationManager.addParameterListener( "enable.seedingonly.maxuploads", listener );
	}


	private static final Map					global_state_cache			= new HashMap();
	private static final ArrayList			global_state_cache_wrappers	= new ArrayList();

	private static final CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> global_listeners_read_map_cow  = new CopyOnWriteMap<>();
	private static final CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> global_listeners_write_map_cow = new CopyOnWriteMap<>();


	private DownloadManagerImpl			download_manager;

	private final TorrentUtils.ExtendedTorrent	torrent;

	private boolean						write_required_soon;
	private long						write_required_sometime = -1;

	private Category 	category;

	private final CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> listeners_read_map_cow  = new CopyOnWriteMap<>();
	private final CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> listeners_write_map_cow = new CopyOnWriteMap<>();


	private Map			parameters;
	private Map			attributes;

	private final AEMonitor	this_mon	= new AEMonitor( "DownloadManagerState" );

	private int supressWrites = 0;

	private boolean recovered;
	
	private static final ThreadLocal		tls_wbr	=
		new ThreadLocal()
		{
			@Override
			public Object
			initialValue()
			{
				return( new ArrayList(1));
			}
		};

	private int transient_flags;
		
	private Map<String,Object>	transient_attributes = new HashMap<>();
			
	private static DownloadManagerStateImpl
	getDownloadState(
		DownloadManagerImpl				download_manager,
		TOTorrent						original_torrent,
		TorrentUtils.ExtendedTorrent	target_torrent )

		throws TOTorrentException
	{
		byte[]	hash	= target_torrent.getHash();

		DownloadManagerStateImpl	res	= null;

		try{
			class_mon.enter();

			HashWrapper	hash_wrapper = new HashWrapper( hash );

			res = (DownloadManagerStateImpl)state_map.get(hash_wrapper);

			if ( res == null ){

				res = new DownloadManagerStateImpl( download_manager, target_torrent );

				state_map.put( hash_wrapper, res );

				if ( debug_on ){

					FileUtil.log("    dms created" );
				}

			}else{

				if ( debug_on ){

					FileUtil.log("    dms found in state_map" );
				}
				
					// if original state was created without a download manager,
					// bind it to this one

				if ( res.getDownloadManager() == null && download_manager != null ){

					res.setDownloadManager( download_manager );
				}

				if ( original_torrent != null ){

					res.mergeTorrentDetails( original_torrent );
				}
			}
		}finally{

			class_mon.exit();
		}

		return( res );
	}


	public static DownloadManagerState
	getDownloadState(
		TOTorrent		original_torrent )

		throws TOTorrentException
	{
		byte[]	torrent_hash = original_torrent.getHash();

		// System.out.println( "getDownloadState: hash = " + ByteFormatter.encodeString(torrent_hash));

		TorrentUtils.ExtendedTorrent saved_state	= null;

		File	saved_file = getStateFile( torrent_hash );

		boolean	was_corrupt = false;
		
		if ( saved_file.exists()){

			try{
				saved_state = TorrentUtils.readDelegateFromFile( saved_file, false );

			}catch( Throwable e ){

				was_corrupt = true;
				
				Debug.out( "Failed to load download state for " + saved_file, e );
			}
		}

			// if saved state not found then recreate from original torrent

		if ( saved_state == null ){

			copyTorrentToActive( original_torrent, saved_file, was_corrupt );

			saved_state = TorrentUtils.readDelegateFromFile( saved_file, false );
		}

		DownloadManagerStateImpl state = getDownloadState( null, original_torrent, saved_state );
		
		if ( was_corrupt ){
			
			state.setRecovered();
		}
		
		return( state );
	}

	protected static DownloadManagerState
	getDownloadState(
		DownloadManagerImpl	download_manager,
		String				torrent_file,
		byte[]				torrent_hash,
		boolean				inactive )

		throws TOTorrentException
	{
		boolean	discard_pieces = state_map.size() > 32;

		if ( debug_on ){

			FileUtil.log("getDownloadState: hash = " + (torrent_hash==null?"null":ByteFormatter.encodeString(torrent_hash) + ", file = " + torrent_file ));
		}
		
		TOTorrent						original_torrent	= null;
		TorrentUtils.ExtendedTorrent 	saved_state			= null;

			// first, if we already have the hash then see if we can load the saved state

		if ( torrent_hash != null ){

			File	saved_file = getStateFile( torrent_hash );

			if ( saved_file.exists()){
				
				if ( debug_on ){

					FileUtil.log("    saved state 1 exists" );
				}

				try{
					Map	cached_state = (Map)global_state_cache.remove( new HashWrapper( torrent_hash ));

					if ( cached_state != null ){

						if ( debug_on ){

							FileUtil.log("    saved state 1: got cached state" );
						}
						
						CachedStateWrapper wrapper = new CachedStateWrapper( download_manager, torrent_file, torrent_hash, cached_state, inactive );

						global_state_cache_wrappers.add( wrapper );

						saved_state	= wrapper;

					}else{

						saved_state = TorrentUtils.readDelegateFromFile( saved_file, discard_pieces );
						
						if ( debug_on ){

							FileUtil.log("    saved state 1: read from " + saved_file );
						}
					}

				}catch( Throwable e ){

					Debug.out( "Failed to load download state for " + saved_file, e );
				}
			}else{
				
				if ( debug_on ){

					FileUtil.log("    saved state 1 doesn't exist" );
				}
			}
		}

		
			// if saved state not found then recreate from original torrent if required

		boolean	was_corrupt = false;

		if ( saved_state == null ){

			original_torrent = TorrentUtils.readDelegateFromFile( FileUtil.newFile(torrent_file), discard_pieces );

			torrent_hash = original_torrent.getHash();

			File	saved_file = getStateFile( torrent_hash );
			
			if ( saved_file.exists()){
				
				if ( debug_on ){

					FileUtil.log("    saved state 2 exists" );
				}
				
				try{
					saved_state = TorrentUtils.readDelegateFromFile( saved_file, discard_pieces );

					if ( debug_on ){

						FileUtil.log("    saved state 2: read from " + saved_file );
					}
				}catch( Throwable e ){

					was_corrupt = true;
					
					Debug.out( "Failed to load download state for " + saved_file );
				}
			}else{
				
				if ( debug_on ){

					FileUtil.log("    saved state 2 doesn't exist" );
				}
			}

			if ( saved_state == null ){

					// we must copy the torrent as we want one independent from the
					// original (someone might still have references to the original
					// and do stuff like write it somewhere else which would screw us
					// up)

				copyTorrentToActive( original_torrent, saved_file, was_corrupt );

				saved_state = TorrentUtils.readDelegateFromFile( saved_file, discard_pieces );
				
				if ( debug_on ){

					FileUtil.log("    saved state 3: read from " + saved_file );
				}
			}
		}

		DownloadManagerStateImpl res = getDownloadState( download_manager, original_torrent, saved_state );

		if ( was_corrupt ){
			
			res.setRecovered();
		}

		if ( inactive ){

			res.setActive( false );
		}

		return( res );
	}

	private static void
	copyTorrentToActive(
		TOTorrent		torrent_file,
		File			state_file,
		boolean			was_corrupt )
	
		throws TOTorrentException
	{
		TorrentUtils.copyToFile( torrent_file, state_file );
		
		if ( was_corrupt ){
			
    		Logger.log(
    			new LogAlert(
    				torrent_file, 
    				LogAlert.REPEATABLE,
					 LogAlert.AT_ERROR,
					"Recovered download from original torrent: " + TorrentUtils.getLocalisedName(torrent_file)));
		}
		
		if (	COConfigurationManager.getBooleanParameter("Save Torrent Files") && 
				COConfigurationManager.getBooleanParameter("Delete Saved Torrent Files")){
			
			try{
					// torrent might not have been persisted yet so don't insist of file name being present
				
				String file_str = TorrentUtils.getTorrentFileName( torrent_file, false  );
				
				if ( file_str != null ){
					
					File file = FileUtil.newFile( file_str );
					
					File torrentDir = FileUtil.newFile(COConfigurationManager.getDirectoryParameter("General_sDefaultTorrent_Directory"));
	
					if ( torrentDir.isDirectory() && torrentDir.equals( file.getParentFile())){
						
						file.delete();

						FileUtil.newFile( file.getAbsolutePath() + ".bak" ).delete();
					}
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
	}
	
	protected static File
	getStateFile(
		byte[]		torrent_hash )
	{
		return( FileUtil.newFile( ACTIVE_DIR, ByteFormatter.encodeString( torrent_hash ) + ".dat" ));
	}

	protected static File
	getGlobalStateFile()
	{
		return( FileUtil.newFile( ACTIVE_DIR, "cache.dat" ));
	}

	public static void
	loadGlobalStateCache()
	{
		File file = getGlobalStateFile();

		if ( !file.canRead()){

			return;
		}

		try( 	FileInputStream fis = FileUtil.newFileInputStream( file );
				BufferedInputStream is = new BufferedInputStream( new GZIPInputStream( fis ))){

			try{

				Map	map = BDecoder.decode( is );

				List	cache = (List)map.get( "state" );

				if ( cache != null ){

					for (int i=0;i<cache.size();i++){

						Map	entry = (Map)cache.get(i);

						byte[]	hash = (byte[])entry.get( "hash" );

						if ( hash != null ){

							global_state_cache.put( new HashWrapper( hash ), entry );
						}
					}
				}

			}catch( IOException e){

				Debug.printStackTrace( e );
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}
	}

	public static void
	saveGlobalStateCache()
	{
		try{
			class_mon.enter();

			Map	map = new HashMap();

			List	cache = new ArrayList();

			map.put( "state", cache );

			Iterator	it = state_map.values().iterator();

			while( it.hasNext()){

				DownloadManagerState dms = (DownloadManagerState)it.next();

				DownloadManager dm = dms.getDownloadManager();

				if ( dm != null && dm.isPersistent()){

					try{
						Map	state = CachedStateWrapper.export( dms );

						cache.add( state );

					}catch( Throwable e ){

						Debug.printStackTrace( e );
					}
				}
			}

			GZIPOutputStream	os = new GZIPOutputStream( FileUtil.newFileOutputStream( getGlobalStateFile()));

			try{

				os.write( BEncoder.encode( map ));

				os.close();

			}catch( IOException e ){

				Debug.printStackTrace( e );

				try{
					os.close();

				}catch( IOException f ){

				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );

		}finally{

			class_mon.exit();

		}
	}

	public static void
	discardGlobalStateCache()
	{
		getGlobalStateFile().delete();

		for ( int i=0;i<global_state_cache_wrappers.size();i++){

			((CachedStateWrapper)global_state_cache_wrappers.get(i)).clearCache();
		}

		global_state_cache_wrappers.clear();
		global_state_cache_wrappers.trimToSize();
	}

	public static void
	importDownloadState(
		File		source_dir,
		byte[]		download_hash )

		throws DownloadManagerException
	{
		String	hash_str = ByteFormatter.encodeString( download_hash );

		String	state_file = hash_str + ".dat";

		File	target_state_file 	= FileUtil.newFile( ACTIVE_DIR, state_file );
		File	source_state_file 	= FileUtil.newFile( source_dir, state_file );

		if ( !source_state_file.exists()){

			throw( new DownloadManagerException( "Source state file missing: " + source_state_file ));
		}

		if ( target_state_file.exists()){

			target_state_file.delete();

			//throw( new DownloadManagerException( "Target state file already exists: " + target_state_file ));
		}

		if ( !FileUtil.copyFile( source_state_file, target_state_file )){

			throw( new DownloadManagerException( "Failed to copy state file: " + source_state_file + " -> " + target_state_file ));
		}

		File	source_state_dir = FileUtil.newFile( source_dir, hash_str );

		if ( source_state_dir.exists()){

			try{
				FileUtil.copyFileOrDirectory( source_state_dir, ACTIVE_DIR );

			}catch( Throwable e ){

				target_state_file.delete();

				throw( new DownloadManagerException( "Failed to copy state dir: " + source_dir + " -> " + ACTIVE_DIR, e ));
			}
		}
	}

	public static void
	deleteDownloadState(
		byte[]		download_hash,
		boolean		delete_cache )

		throws DownloadManagerException
	{
		deleteDownloadState( ACTIVE_DIR, download_hash );
		
		if ( delete_cache ){
			
			try{
				class_mon.enter();

				HashWrapper	wrapper = new HashWrapper( download_hash );

				state_map.remove( wrapper );
				
			}finally{
				
				class_mon.exit();
			}
		}
	}

	public static void
	deleteDownloadState(
		File		source_dir,
		byte[]		download_hash )

		throws DownloadManagerException
	{
		String	hash_str = ByteFormatter.encodeString( download_hash );

		String	state_file = hash_str + ".dat";

		File	target_state_file 	= FileUtil.newFile( source_dir, state_file );

		if ( target_state_file.exists()){

			if ( !target_state_file.delete()){

				throw( new DownloadManagerException( "Failed to delete state file: " + target_state_file ));
			}
		}

		File target_state_file_bak = FileUtil.newFile( source_dir, state_file + ".bak" );
		
		if ( target_state_file_bak.exists()){

			if ( !target_state_file_bak.delete()){

				throw( new DownloadManagerException( "Failed to delete state backup file: " + target_state_file_bak ));
			}
		}

		File	target_state_dir = FileUtil.newFile( source_dir, hash_str );

		if ( target_state_dir.exists()){

			if ( !FileUtil.recursiveDelete( target_state_dir )){

				throw( new DownloadManagerException( "Failed to delete state dir: " + target_state_dir ));
			}
		}
	}

	protected
	DownloadManagerStateImpl(
		DownloadManagerImpl				_download_manager,
		TorrentUtils.ExtendedTorrent	_torrent )
	{
		download_manager	= _download_manager;
		torrent				= _torrent;

		attributes = torrent.getAdditionalMapProperty( ATTRIBUTE_KEY );

		if ( attributes == null ){

			attributes	= new HashMap();
        }

        String cat_string = getStringAttribute( AT_CATEGORY );

        if ( cat_string != null ){

        	Category cat = CategoryManager.getCategory( cat_string );

        	if ( cat != null ){

        		setCategory( cat );
        	}
        }

        parameters	= getMapAttribute( AT_PARAMETERS );

        if ( parameters == null ){

        	parameters	= new HashMap();
        }

        	// note that version will be -1 for the first time through this code

        int	version = getIntAttribute( AT_VERSION );

        if ( version < VER_HOLE_PUNCH_PEER_SOURCE ){

        		// migrate by adding as enabled - only needed if we have any specified as other
        		// code takes care of the case where we have none

        	if ( getPeerSources().length > 0 ){

        		if ( PEPeerSource.isPeerSourceEnabledByDefault( PEPeerSource.PS_HOLE_PUNCH )){

        			setPeerSourceEnabled( PEPeerSource.PS_HOLE_PUNCH, true );
        		}
        	}else{

        			// set default for newly added torrent

				setPeerSources( PEPeerSource.getDefaultEnabledPeerSources());
        	}
        }

        long flags = getFlags();

        if (( flags & FLAG_DISABLE_IP_FILTER ) != 0 ){

        	try{
        		IpFilterManagerFactory.getSingleton().getIPFilter().addExcludedHash( torrent.getHash());

        	}catch( Throwable e ){

        		Debug.out( e );
        	}
        }

        if ( version < VER_CURRENT ){

        	setIntAttribute( AT_VERSION, VER_CURRENT );
        }
	}

	@Override
	public void
	parameterChanged(
		String parameterName)
	{
			// get any listeners to pick up new values as their defaults are based on core params

		informWritten( AT_PARAMETERS );
	}

	@Override
	public DownloadManager
	getDownloadManager()
	{
		return( download_manager );
	}

	protected void
	setDownloadManager(
		DownloadManagerImpl		dm )
	{
		download_manager	= dm;
	}

	@Override
	public File
	getStateFile( )
	{
		try{
			File	parent = FileUtil.newFile( ACTIVE_DIR, ByteFormatter.encodeString( torrent.getHash()));

			return( StringInterner.internFile(parent));

		}catch( Throwable e ){

			Debug.printStackTrace(e);

			return( null );
		}
	}

	private void
	setRecovered()
	{
		recovered = true;
	}
	
	@Override
	public boolean
	getAndClearRecoveredStatus()
	{
		if ( recovered ){
			
			recovered = false;
			
			return( true );
		}
		
		return( false );
	}
	
	@Override
	public void
	clearTrackerResponseCache()
	{
		setTrackerResponseCache( new HashMap());
	}

	@Override
	public Map getTrackerResponseCache() {

		Map tracker_response_cache = null;

		tracker_response_cache = torrent.getAdditionalMapProperty(TRACKER_CACHE_KEY);

		if (tracker_response_cache == null)
			tracker_response_cache = new HashMap();

		return (tracker_response_cache);
	}

	@Override
	public void
	setTrackerResponseCache(
		Map		value )
	{

		try{
			this_mon.enter();

			// System.out.println( "setting download state/tracker cache for '" + new String(torrent.getName()));

			boolean	changed = !BEncoder.mapsAreIdentical( value, getTrackerResponseCache() );

			if ( changed ){

				setDirty( false );

				torrent.setAdditionalMapProperty( TRACKER_CACHE_KEY, value );
			}

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public Map
	getResumeData()
	{
		try{
			this_mon.enter();

			return( torrent.getAdditionalMapProperty(RESUME_KEY));

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	clearResumeData()
	{
		setResumeData( null );
	}

	@Override
	public void
	setResumeData(
		Map	new_data )
	{
		boolean changed = false;

		try{
			this_mon.enter();

			// System.out.println( "setting download state/resume data for '" + new String(torrent.getName()));
			
			Map existing_data = torrent.getAdditionalMapProperty(RESUME_KEY  );
			
			boolean existing_was_valid = existing_data != null && DiskManagerFactory.isTorrentResumeDataValid( this );
			
			long new_resume_state;
			
			if ( new_data == null ){

				new_resume_state = 1;
								
				if ( existing_data != null ){
				
					torrent.removeAdditionalProperty( RESUME_KEY );

					changed = true;
				}
			}else{

				changed = !BEncoder.mapsAreIdentical( existing_data, new_data );

				torrent.setAdditionalMapProperty( RESUME_KEY, new_data );
				
				boolean complete = DiskManagerFactory.isTorrentResumeDataComplete( this );

				new_resume_state = complete?2:1;
			}
			
			if ( getLongAttribute( AT_RESUME_STATE ) != new_resume_state ){
				
				setLongAttribute( AT_RESUME_STATE, new_resume_state );
				
				changed = true;
			}

			if ( changed ){
			
				if ( existing_was_valid ){
										
					Map history = torrent.getAdditionalMapProperty( RESUME_HISTORY_KEY  );
					
					List<Long>	h_dates;
					List<Map>	h_resumes;
					
					if ( history == null ){
						
						history = new HashMap();
						
						h_dates 	= new ArrayList<>();
						h_resumes	= new ArrayList<>();
						
						history.put( "dates", h_dates );
						
						history.put( "resumes", h_resumes );
						
					}else{
						
						h_dates		= (List<Long>)history.get( "dates" );
						
						h_resumes	 = (List<Map>)history.get( "resumes" );
					}
					
					boolean found = false;
					
					for ( Map m: h_resumes ){
						
						if ( BEncoder.mapsAreIdentical( existing_data, m )){
							
							found = true;
							
							break;
						}
					}
					
					if ( !found ){
						
						h_dates.add( SystemTime.getCurrentTime());
						
						h_resumes.add( existing_data );
						
						if ( h_dates.size() > 3 ){
							
							h_dates.remove( 0 );
							
							h_resumes.remove( 0 );
						}
						
						torrent.setAdditionalMapProperty( RESUME_HISTORY_KEY, history  );
					}
				}
				
				setDirty( false );
			}

		}finally{

			this_mon.exit();
		}

			// we need to ensure this is persisted now as it has implications regarding crash restarts etc

		if ( disable_interim_saves && !changed ){
			
				// to be honest if the data hasn't changed I'm not sure why we need a save at all here but for the moment
				// I'll disable it if the user has already opted in to reducing writes
			
		}else{
		
			saveSupport(false,false);
		}
	}

	@Override
	public boolean
	isResumeDataComplete()
	{
			// this is a cache of resume state to speed up startup

		long	state = getLongAttribute( AT_RESUME_STATE );

		if ( state == 0 ){

			// don't know

			boolean complete = DiskManagerFactory.isTorrentResumeDataComplete( this );

			setLongAttribute( AT_RESUME_STATE, complete?2:1 );

			return( complete );

		}else{

			return( state == 2 );
		}
	}
	
	@Override
	public List<ResumeHistory> 
	getResumeDataHistory()
	{
		List<ResumeHistory> result = new ArrayList<>();

		try{
			this_mon.enter();
						
			Map history = torrent.getAdditionalMapProperty( RESUME_HISTORY_KEY  );
			
			List<Long>	h_dates;
			List<Map>	h_resumes;
			
			if ( history != null ){
					
				h_dates		= (List<Long>)history.get( "dates" );
				
				h_resumes	 = (List<Map>)history.get( "resumes" );
				
				int pos = 0;
				
				Map existing_data = getResumeData();
				
				for ( Long date: h_dates ){
					
					Map resume = h_resumes.get( pos++ );
					
					if ( existing_data == null || !BEncoder.mapsAreIdentical( existing_data, resume )){
					
						result.add( new ResumeHistoryImpl( date, resume));
					}
				}
			}			
		}finally{

			this_mon.exit();
		}
		
		return( result );
	}
	
	static class
	ResumeHistoryImpl
		implements ResumeHistory
	{
		final long 		date;
		final Map		resume_data;
		
		ResumeHistoryImpl(
			long	_date,
			Map		_resume_data )
		{
			date		= _date;
			resume_data	= _resume_data;
		}
		
		public long 
		getDate()
		{
			return( date );
		}
	}
	
	@Override
	public void 
	restoreResumeData(
		ResumeHistory history)
	{
		download_manager.restoreResumeData( ((ResumeHistoryImpl)history).resume_data );
	}

	@Override
	public TOTorrent
	getTorrent()
	{
		return( torrent );
	}

	@Override
	public void
	setActive(
		boolean		active )
	{
		torrent.setDiscardFluff( !active );
	}

	@Override
	public void discardFluff()
	{
		torrent.setDiscardFluff(true);
	}

	@Override
	public boolean
	exportState(
		File	target_dir )
	{
		try{
			this_mon.enter();

			saveSupport( false, true );

			byte[]	hash = torrent.getHash();

			String	hash_str = ByteFormatter.encodeString( hash );

			String	state_file = hash_str + ".dat";

			File	existing_state_file = FileUtil.newFile( ACTIVE_DIR, state_file );
			File	target_state_file 	= FileUtil.newFile( target_dir, state_file );

			if ( !FileUtil.copyFile( existing_state_file, target_state_file )){

				throw( new IOException( "Failed to copy state file" ));
			}

			File	existing_state_dir = FileUtil.newFile( ACTIVE_DIR, hash_str );

			if ( existing_state_dir.exists()){

				FileUtil.copyFileOrDirectory( existing_state_dir, target_dir );
			}

			return( true );

		}catch( Throwable e ){

			Debug.out( e );

			return( false );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void suppressStateSave(boolean suppress) {
		if(suppress)
			supressWrites++;
		else if(supressWrites > 0)
			supressWrites--;
	}


	private void
	setDirty(
		boolean		slightly )
	{
		//Debug.out( (slightly?"slightly":"dirty" )+ ": " + new String(torrent.getName()));
				
		if ( slightly ){
			
			if ( write_required_sometime == -1 ){
			
				write_required_sometime = SystemTime.getMonotonousTime();
			}
			
		}else{
		
			write_required_soon = true;
		}
	}
	
	@Override
	public void
	save( 
		boolean interim )
	{
		saveSupport( interim, false );
	}

	protected void
	saveSupport( 
		boolean interim, 
		boolean force )
	{
		if ( !force ){
			
			if ( supressWrites > 0 ){
	
				return;
			}
			
			if ( interim && disable_interim_saves ){
				
				return;
			}
		}
		
		//boolean soon = write_required_soon;
		//long some = write_required_sometime;

 		boolean do_write;

		try{
			this_mon.enter();

			if ( write_required_soon ){
				
				do_write = true;
				
			}else if ( write_required_sometime != -1 ){
					
				if ( interim ){
				
						// avoid all the 'sometime' writes ending up being done on closedown
					
					do_write = SystemTime.getMonotonousTime() - write_required_sometime > 5*60*1000;
				
				}else{
					
					do_write = true;
				}
			}else{
				
				do_write = false;
			}
			
			if ( do_write ){
			
				write_required_soon 	= false;
				write_required_sometime	= -1;
			}
		}finally{

			this_mon.exit();
		}

		if ( do_write ){

			try{
				
				//Debug.out( "writing download state for '" + new String(torrent.getName()) + ", interim=" + interim + ", soon/some=" + soon + "/" + some );

				if (Logger.isEnabled())
					Logger.log(new LogEvent(torrent, LOGID, "Saving state for download '"
							+ TorrentUtils.getLocalisedName(torrent) + "'"));

				/*
				if ( save_log != null ){
					
					save_log.log( TorrentUtils.getLocalisedName(torrent) + ": " + Debug.getCompressedStackTrace());
				}
				*/
				
				torrent.setAdditionalMapProperty( ATTRIBUTE_KEY, attributes );

				TorrentUtils.writeToFile(torrent, true);

			}catch ( Throwable e ){
				
				Logger.log(new LogEvent(torrent, LOGID, "Saving state", e));
			}
		}else{

			// System.out.println( "not writing download state for '" + new String(torrent.getName()));
		}
	}

	@Override
	public void
	delete()
	{
		try{
			class_mon.enter();

			HashWrapper	wrapper = torrent.getHashWrapper();

			boolean removed = state_map.remove( wrapper ) != null;

			if ( debug_on ){

				FileUtil.log("deleteDownloadState: hash = " + (wrapper==null?"null":ByteFormatter.encodeString(wrapper.getBytes())) + ", removed=" + removed );
			}

	        TorrentUtils.delete( torrent );

	        String	hash_str = ByteFormatter.encodeString( wrapper.getBytes());
	        
			String	state_file = hash_str + ".dat";
			
			File	target_state_file 	= FileUtil.newFile( ACTIVE_DIR, state_file );

			if ( target_state_file.exists()){

				if ( !target_state_file.delete()){

					throw( new DownloadManagerException( "Failed to delete state file: " + state_file ));
				}
			}

			FileUtil.newFile( ACTIVE_DIR, state_file + ".bak" ).delete();
			
			File	dir = FileUtil.newFile( ACTIVE_DIR, hash_str );

			if ( dir.exists() && dir.isDirectory()){

				FileUtil.recursiveDelete( dir );
			}
		}catch( Throwable e ){

	    	Debug.printStackTrace( e );

		}finally{

			class_mon.exit();
		}
	}

	protected void
	mergeTorrentDetails(
		TOTorrent	other_torrent )
	{
		try{
			boolean	write = TorrentUtils.mergeAnnounceURLs( other_torrent, torrent );

			// System.out.println( "DownloadManagerState:mergeTorrentDetails -> " + write );

			if ( write ){

				saveSupport(false,false);

				if ( download_manager != null ){

					TRTrackerAnnouncer	client = download_manager.getTrackerClient();

					if ( client != null ){

						// pick up any URL changes

						client.resetTrackerUrl( false );
					}
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}
	}

	@Override
	public void
	setFlag(
		long		flag,
		boolean		set )
	{
		long	old_value = getLongAttribute( AT_FLAGS );

		long	new_value;

		if ( set ){

			new_value = old_value | flag;

		}else{

			new_value = old_value & ~flag;
		}

		if ( old_value != new_value ){

			setLongAttribute( AT_FLAGS, new_value );

			if (( old_value & FLAG_DISABLE_IP_FILTER ) != ( new_value & FLAG_DISABLE_IP_FILTER )){

	        	try{
	        		if (( new_value & FLAG_DISABLE_IP_FILTER ) != 0 ){

	        			IpFilterManagerFactory.getSingleton().getIPFilter().addExcludedHash( torrent.getHash());

	        		}else{

	        			IpFilterManagerFactory.getSingleton().getIPFilter().removeExcludedHash( torrent.getHash());

	        		}
	        	}catch( Throwable e ){

	        		Debug.out( e );
	        	}
	        }
		}
	}

	@Override
	public boolean
	getFlag(
		long	flag )
	{
		long	value = getLongAttribute( AT_FLAGS );

		return(( value & flag ) != 0 );
	}

	@Override
	public long
	getFlags()
	{
		return( getLongAttribute( AT_FLAGS ));
	}

	public void
	setTransientFlag(
		long		flag,
		boolean		set )
	{
		long old_value = transient_flags;
		
		long new_value;
		
		if ( set ){
			
			new_value = old_value | flag;
			
		}else{
			
			new_value = old_value & ~flag;
		}
		
		if ( old_value != new_value ){
			
			transient_flags = (int)new_value;
			
			informWritten( AT_TRANSIENT_FLAGS );
		}
	}

	public boolean
	getTransientFlag(
		long		flag )
	{
		return(( transient_flags & flag ) != 0 );
	}

	public long
	getTransientFlags()
	{
		return( transient_flags );
	}
	
	@Override
	public Object 
	getTransientAttribute(
		String name )
	{
		synchronized( transient_attributes ){
			
			return( transient_attributes.get( name ));
		}
	}
	
	@Override
	public void 
	setTransientAttribute(
		String 	name, 
		Object 	value)
	{
		synchronized( transient_attributes ){
			
			transient_attributes.put( name, value );
		}
		
		// informWritten( name ); // not needed yet...
	}
	
	@Override
	public boolean parameterExists(String name) {
		return parameters.containsKey(name);
	}

	@Override
	public void
	setParameterDefault(
		String	name )
	{
		try{
			this_mon.enter();

			Object	value = parameters.get( name );

			if ( value == null ){

				return;
			}

				// gotta clone here otherwise we update the underlying  map and the setMapAttribute code
				// doesn't think it has changed

			parameters	= new LightHashMap(parameters);

			parameters.remove( name );

		}finally{

			this_mon.exit();
		}

		setMapAttribute( AT_PARAMETERS, parameters );
	}

	@Override
	public long
	getLongParameter(
		String	name )
	{
		try{
			this_mon.enter();

			Object	value = parameters.get( name );

			if ( value == null ){

				value = default_parameters.get( name );

				if ( value == null ){

					Debug.out( "Unknown parameter '" + name + "' - must be defined in DownloadManagerState" );

					return( 0 );

				}else{

					value = getDefaultOverride( name, value );
					
					if ( name == PARAM_RANDOM_SEED ){

						setLongParameter( name, (Long)value );
					}
				}
			}

			if ( value instanceof Boolean ){

				return(((Boolean)value).booleanValue()?1:0);

			}else if ( value instanceof Integer ){

				return( ((Integer)value).longValue());

			}else if ( value instanceof Long ){

				return( ((Long)value).longValue());
			}

			Debug.out( "Invalid parameter value for '" + name + "' - " + value );

			return( 0 );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	setLongParameter(
		String		name,
		long		value )
	{
		Object	default_value = default_parameters.get( name );

		if ( default_value == null ){

			Debug.out( "Unknown parameter '" + name + "' - must be defined in DownloadManagerState" );
		}

		try{
			this_mon.enter();

				// gotta clone here otherwise we update the underlying  map and the setMapAttribute code
				// doesn't think it has changed

			parameters	= new LightHashMap(parameters);

			parameters.put( name, new Long(value));

			setMapAttribute( AT_PARAMETERS, parameters );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public int
	getIntParameter(
		String	name )
	{
		return( (int)getLongParameter( name ));
	}

	@Override
	public void
	setIntParameter(
		String	name,
		int		value )
	{
		setLongParameter( name, value );
	}

	@Override
	public boolean
	getBooleanParameter(
		String	name )
	{
		return( getLongParameter( name ) != 0 );
	}

	@Override
	public void
	setBooleanParameter(
		String		name,
		boolean		value )
	{
		setLongParameter( name, value?1:0 );
	}

	@Override
	public void
	setAttribute(
		String		name,
		String		value )
	{
		setAttribute( name,value, true );
	}
	
	@Override
	public void
	setAttribute(
		String		name,
		String		value,
		boolean		set_dirty )
	{

		if ( name.equals( AT_CATEGORY )){

			if ( value == null ){

				setCategory( null );

			}else{
				Category	cat = CategoryManager.getCategory( value );

				if ( cat == null ){

					cat = CategoryManager.createCategory( value );

				}

				setCategory( cat );
			}
			return;
		}

		if (name.equals(AT_RELATIVE_SAVE_PATH)) {
			if (value.length() > 0) {
				File relative_path_file = FileUtil.newFile(value);
				relative_path_file = DownloadManagerDefaultPaths.normaliseRelativePath(relative_path_file);
				value = (relative_path_file == null) ? "" : relative_path_file.getPath();
			}
		}

		setStringAttribute( name, value, set_dirty );
	}

	@Override
	public String
	getAttribute(
		String		name )
	{
		if ( name.equals( AT_CATEGORY )){

			Category	cat = getCategory();

			if ( cat == null ){

				return( null );
			}

			if ( cat == CategoryManager.getCategory( Category.TYPE_UNCATEGORIZED )){

				return( null );
			}

			return( cat.getName());

		}else{

			return( getStringAttribute( name ));
		}
	}

	@Override
	public
	Category
	getCategory()
	{
	    return category;
	}

	@Override
	public void
	setCategory(
		Category 	cat )
	{
		if ( cat == category ){
			return;
		}

		if (cat != null && cat.getType() != Category.TYPE_USER){

			cat = null;
			if (cat == category) {
				return;
			}
		}

		Category oldCategory = (category == null)?CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED):category;

		category = cat;

		if (oldCategory != null ){

			oldCategory.removeManager( this );
  		}

		DownloadManager dm = getDownloadManager();

		if ( dm != null && !dm.isDestroyed()){

			if ( category != null ){

				category.addManager( this );

			} else {

				CategoryManager.getCategory(Category.TYPE_UNCATEGORIZED).addManager(this);
			}
		}

		if ( category != null ){

			setStringAttribute( AT_CATEGORY, category.getName(), true );

		}else{

			setStringAttribute( AT_CATEGORY, null, true );
		}
	}

	@Override
	public String
	getTrackerClientExtensions()
	{
		return( getStringAttribute( AT_TRACKER_CLIENT_EXTENSIONS ));
	}

	@Override
	public void
	setTrackerClientExtensions(
		String		value )
	{
		setStringAttribute( AT_TRACKER_CLIENT_EXTENSIONS, value, true );
	}

    @Override
    public String getDisplayName() {
    	return this.getStringAttribute(AT_DISPLAY_NAME);
    }

    @Override
    public void setDisplayName(String value) {
    	this.setStringAttribute(AT_DISPLAY_NAME, value, true);
    }

    @Override
    public String getUserComment() {
    	return this.getStringAttribute(AT_USER_COMMENT);
    }

    @Override
    public void setUserComment(String value) {
    	this.setStringAttribute(AT_USER_COMMENT, value, true);
    }

    @Override
    public String getRelativeSavePath() {
    	return this.getStringAttribute(AT_RELATIVE_SAVE_PATH);
    }

	@Override
	public DiskManagerFileInfo getPrimaryFile() {
		int primaryIndex = -1;
		DiskManagerFileInfo[] fileInfo = download_manager.getDiskManagerFileInfoSet().getFiles();
		if (hasAttribute(AT_PRIMARY_FILE_IDX)) {
			primaryIndex = getIntAttribute(AT_PRIMARY_FILE_IDX);
		}

		if (primaryIndex < 0 || primaryIndex >= fileInfo.length) {
			primaryIndex = -1;
			if (fileInfo.length > 0) {
				int idxBiggest = -1;
				long lBiggest = -1;
				int numChecked = 0;
				for (int i = 0; i < fileInfo.length && numChecked < 10; i++) {
					if (!fileInfo[i].isSkipped()) {
						numChecked++;
						if (fileInfo[i].getLength() > lBiggest) {
  						lBiggest = fileInfo[i].getLength();
  						idxBiggest = i;
						}
					}
				}
				if (idxBiggest >= 0) {
					primaryIndex = idxBiggest;
				}
			}
			if (primaryIndex >= 0) {
				setPrimaryFile(fileInfo[primaryIndex]);
			}
		}

		if (primaryIndex >= 0) {
			return fileInfo[primaryIndex];
		}
		return null;
	}

	/**
	 * @param dmfi
	 */
	@Override
	public void setPrimaryFile(DiskManagerFileInfo dmfi) {
		setIntAttribute(AT_PRIMARY_FILE_IDX, dmfi.getIndex());
	}

	@Override
	public String[]
	getNetworks()
	{
		List	values = getListAttributeSupport( AT_NETWORKS );

		List	res = new ArrayList();

			// map back to the constants to allow == comparisons

		for (int i=0;i<values.size();i++){

			String	nw = (String)values.get(i);

			for (int j=0;j<AENetworkClassifier.AT_NETWORKS.length;j++){

				String	nn = AENetworkClassifier.AT_NETWORKS[j];

				if ( nn.equals( nw )){

					res.add( nn );
				}
			}
		}

		String[]	x = new String[res.size()];

		res.toArray(x);

		return( x );
	}

	  @Override
	  public boolean isNetworkEnabled(
	      String network) {
	    List	values = getListAttributeSupport( AT_NETWORKS );
	    return values.contains(network);
	  }

	@Override
	public void
	setNetworks(
		String[]		networks )
	{
		if ( networks == null ){

			networks = new String[0];
		}

		List	l = new ArrayList();

		Collections.addAll(l, networks);

		setListAttribute( AT_NETWORKS, l );
	}

	  @Override
	  public void
	  setNetworkEnabled(
	      String network,
	      boolean enabled) {
	    List	values = getListAttributeSupport( AT_NETWORKS );
	    boolean alreadyEnabled = values.contains(network);

	    if(enabled && !alreadyEnabled) {
	    	List<String> l = new ArrayList<>(values.size() + 1);
		    l.addAll(values);
	      l.add(network);
	      setListAttribute( AT_NETWORKS, l );
	    }
	    if(!enabled && alreadyEnabled) {
		    List<String> l = new ArrayList<>(values);
	      l.remove(network);
	      setListAttribute( AT_NETWORKS, l );
	    }
	  }

		// peer sources

	@Override
	public String[]
	getPeerSources()
	{
		List	values = getListAttributeSupport( AT_PEER_SOURCES );

		List	res = new ArrayList();

			// map back to the constants to allow == comparisons

		for (int i=0;i<values.size();i++){

			String	ps = (String)values.get(i);

			for (int j=0;j<PEPeerSource.PS_SOURCES.length;j++){

				String	x = PEPeerSource.PS_SOURCES[j];

				if ( x.equals( ps )){

					res.add( x );
				}
			}
		}

		String[]	x = new String[res.size()];

		res.toArray(x);

		return( x );
	}

	@Override
	public boolean
	isPeerSourceEnabled(
		String peerSource )
	{
		List	values = getListAttributeSupport( AT_PEER_SOURCES );

		return values.contains(peerSource);
	}

	@Override
	public boolean
	isPeerSourcePermitted(
		String	peerSource )
	{
			// no DHT for private torrents or explicitly prevented

		if ( peerSource.equals( PEPeerSource.PS_DHT )){

			if ( 	TorrentUtils.getPrivate( torrent ) ||
					!TorrentUtils.getDHTBackupEnabled( torrent )){

				return( false );
			}
		}

			// no PEX for private torrents

		if ( peerSource.equals( PEPeerSource.PS_OTHER_PEER )){

			if ( TorrentUtils.getPrivate( torrent )){

				return( false );
			}
		}

		List	values = getListAttributeSupport( AT_PEER_SOURCES_DENIED );

		if ( values != null ){

			if ( values.contains( peerSource )){

				return( false );
			}
		}

		return( true );
	}

	@Override
	public void
	setPeerSourcePermitted(
		String	peerSource,
		boolean	enabled )
	{
		if ( !getFlag( FLAG_ALLOW_PERMITTED_PEER_SOURCE_CHANGES )){

			Logger.log(new LogEvent(torrent, LOGID, "Attempt to modify permitted peer sources denied as disabled '"
							+ TorrentUtils.getLocalisedName(torrent) + "'"));

			return;
		}

		if ( !enabled ){

			setPeerSourceEnabled( peerSource, false );
		}

		List	values = getListAttributeSupport( AT_PEER_SOURCES_DENIED );

		if ( values == null ){

			if ( !enabled ){

				values = new ArrayList();

				values.add( peerSource );

				setListAttribute( AT_PEER_SOURCES_DENIED, values );
			}
		}else{

			if ( enabled ){

				values.remove( peerSource );

			}else{

				if ( !values.contains( peerSource )){

					values.add( peerSource );
				}
			}

			setListAttribute( AT_PEER_SOURCES_DENIED, values );
		}
	}

	@Override
	public void
	setPeerSources(
		String[]		ps )
	{
		if ( ps == null ){

			ps = new String[0];
		}

		List	l = new ArrayList();

		for (int i=0;i<ps.length;i++){

			String	p = ps[i];

			if ( isPeerSourcePermitted(p)){

				l.add( ps[i]);
			}
		}

		setListAttribute( AT_PEER_SOURCES, l );
	}

	  @Override
	  public void
	  setPeerSourceEnabled(
	      String source,
	      boolean enabled )
	  {
		  if ( enabled && !isPeerSourcePermitted( source )){

			  return;
		  }

		  List	values = getListAttributeSupport( AT_PEER_SOURCES );
		  boolean alreadyEnabled = values.contains(source);

		  if(enabled && !alreadyEnabled) {
			  List<String> l = new ArrayList<>(values.size() + 1);
			  l.addAll(values);
		    l.add(source);
		    setListAttribute( AT_PEER_SOURCES, l );
		  }
		  if(!enabled && alreadyEnabled) {
			  List<String> l = new ArrayList<>(values);
		    l.remove(source);
		    setListAttribute( AT_PEER_SOURCES, l );
		  }
	  }


	  // links stuff

	private volatile WeakReference<LinkFileMap>				file_link_cache 	= null;

	@Override
	public void
	setFileLink(
		int		source_index,
		File	link_source,
		File	link_destination )
	{
		LinkFileMap	links = getFileLinks();

		File	existing = (File)links.get( source_index, link_source);

		if ( link_destination == null ){

			if ( existing == null ){

				return;
			}
		}else if ( existing != null && existing.getAbsolutePath().equals( link_destination.getAbsolutePath())){
			
			return;
		}

		links.put( source_index, link_source, link_destination );

		List	list = new ArrayList();

		Iterator<LinkFileMap.Entry>	it = links.entryIterator();

		while( it.hasNext()){

			LinkFileMap.Entry	entry = it.next();

			int		index	= entry.getIndex();
			File	source 	= entry.getFromFile();
			File	target 	= entry.getToFile();

			String	str = index + "\n" + source + "\n" + (target==null?"":target.toString());

			list.add( str );
		}

		//System.out.println( "setFileLink: " + link_source + " -> " + link_destination );

		synchronized( this ){

			file_link_cache = new WeakReference<>(links);
		}

		setListAttribute( AT_FILE_LINKS2, list );
		
		download_manager.informLocationChange( source_index );
	}

	@Override
	public void
	setFileLinks(
		List<Integer>	source_indexes,
		List<File>		link_sources,
		List<File>		link_destinations )
	{
		LinkFileMap	links = getFileLinks();

		boolean changed = false;

		for ( int i=0;i<link_sources.size();i++){

			int		source_index		= source_indexes.get( i );
			File	link_source 		= link_sources.get(i);
			File	link_destination 	= link_destinations.get(i);

			File	existing = links.get( source_index, link_source);

			if ( link_destination == null ){

				if ( existing == null ){

					continue;
				}
			}else if ( existing != null && existing.getAbsolutePath().equals( link_destination.getAbsolutePath())){

				continue;
			}

			links.put( source_index, link_source, link_destination );

			changed = true;
		}

		if ( !changed ){

			return;
		}

		//System.out.println( "setFileLinks: " + links.getString());

		List	list = new ArrayList();

		Iterator<LinkFileMap.Entry>	it = links.entryIterator();

		while( it.hasNext()){

			LinkFileMap.Entry	entry = it.next();

			int		index	= entry.getIndex();
			File	source 	= entry.getFromFile();
			File	target 	= entry.getToFile();

			String	str = index + "\n" + source + "\n" + (target==null?"":target.toString());

			list.add( str );
		}

		synchronized( this ){

			file_link_cache = new WeakReference<>(links);
		}

		setListAttribute( AT_FILE_LINKS2, list );
		
		download_manager.informLocationChange( null );
	}

	@Override
	public void
	clearFileLinks()
	{
		LinkFileMap	links = getFileLinks();

		List	list = new ArrayList();

		Iterator<LinkFileMap.Entry>	it = links.entryIterator();

		boolean	changed = false;

		while( it.hasNext()){

			LinkFileMap.Entry	entry = it.next();

			int		index	= entry.getIndex();
			File	source 	= entry.getFromFile();
			File	target 	= entry.getToFile();

			if ( target != null ){

				changed = true;
			}

			String	str = index + "\n" + source + "\n";

			list.add( str );
		}

		if ( changed ){

			synchronized( this ){

				file_link_cache = null;
			}

			setListAttribute( AT_FILE_LINKS2, list );
			
			download_manager.informLocationChange( null );
		}
	}

	@Override
	public File
	getFileLink(
		int		source_index,
		File	link_source )
	{
		LinkFileMap map = null;

		WeakReference<LinkFileMap> ref = file_link_cache;

		if ( ref != null ){

			map = ref.get();
		}

		if ( map == null ){

			map = getFileLinks();

			synchronized( this ){

				file_link_cache = new WeakReference<>(map);
			}
		}

		File res = map.get( source_index, link_source );

		//System.out.println( "getFileLink: " + link_source + " -> " + res );

		return( res );
	}

	@Override
	public LinkFileMap
	getFileLinks()
	{
		LinkFileMap map = null;

		WeakReference<LinkFileMap> ref = file_link_cache;

		if ( ref != null ){

			map = ref.get();
		}

		if ( map == null ){

			map = getFileLinksSupport();

			synchronized( this ){

				file_link_cache = new WeakReference<>(map);
			}
		}

		return( map );
	}

	private LinkFileMap
	getFileLinksSupport()
	{
		LinkFileMap	res = new LinkFileMap();

		List	new_values = getListAttributeSupport( AT_FILE_LINKS2 );

		if ( new_values.size() > 0 ){

			for (int i=0;i<new_values.size();i++){

				String	entry = (String)new_values.get(i);

				String[] bits = entry.split( "\n" );

				if ( bits.length >= 2 ){

					try{
						int		index 	= Integer.parseInt( bits[0].trim());
						File	source	= FileUtil.newFile(bits[1]);
						File	target	= bits.length<3?null:FileUtil.newFile(bits[2]);

						if( index >= 0 ){

							res.put( index, source, target );

						}else{

								// can get here when partially resolved link state is saved and then re-read

							res.putMigration( source, target );
						}
					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}
		}else{

			List	old_values = getListAttributeSupport( AT_FILE_LINKS_DEPRECATED );

			for (int i=0;i<old_values.size();i++){

				String	entry = (String)old_values.get(i);

				int	sep = entry.indexOf( "\n" );

				if ( sep != -1 ){

					File target = (sep == entry.length()-1)?null:FileUtil.newFile( entry.substring( sep+1 ));

					res.putMigration( FileUtil.newFile( entry.substring(0,sep)), target );
				}
			}
		}

		//System.out.println( "getFileLinks: " + res.getString());

		return( res );
	}

	@Override
	public int getFileFlags(int file_index){
		Map map  = getMapAttribute( AT_FILE_FLAGS );
		
		if ( map == null ){
			
			return(0);
		}
		
		String key = String.valueOf( file_index );
		
		Number result = (Number)map.get( key );
		
		return( result==null?0:result.intValue());
	}

	@Override
	public void setFileFlags(int file_index, int flags){
		Map map  = getMapAttribute( AT_FILE_FLAGS );
		
		if ( map == null ){
			
			map = new HashMap();
			
		}else{
			
			map = BEncoder.cloneMap( map );
		}
		
		String key = String.valueOf( file_index );

		if ( flags == 0 ){
			
			map.remove( key );
			
			if ( map.isEmpty()){
				
				map = null;
			}
		}else{
			
			map.put( key,  flags );
			
		}
		
		setMapAttribute( AT_FILE_FLAGS, map );
	}
	    
	@Override
	public boolean
	isOurContent()
	{
			// HACK!

		Map mapAttr = getMapAttribute("Plugin.azdirector.ContentMap");

		return mapAttr != null	&& mapAttr.containsKey("DIRECTOR PUBLISH");
	}

		// general stuff


	protected String
	getStringAttribute(
		String	attribute_name )
	{
		informWillRead( attribute_name );

		try{
			this_mon.enter();

			if ( !(attributes.get( attribute_name) instanceof byte[] )){

				return( null );
			}

			byte[]	bytes = (byte[])attributes.get( attribute_name );

			if ( bytes == null ){

				return( null );
			}

			return new String(bytes, Constants.DEFAULT_ENCODING_CHARSET);

		}finally{

			this_mon.exit();
		}
	}

	protected void
	setStringAttribute(
		final String	attribute_name,
		final String	attribute_value,
		boolean			set_dirty )
	{
		boolean	changed	= false;

		try{
			this_mon.enter();

			if ( attribute_value == null ){

				if ( attributes.containsKey( attribute_name )){

					attributes.remove( attribute_name );

					changed = true;
					
					if ( set_dirty ){
					
						setDirty( attribute_name == DownloadManagerState.AT_AGGREGATE_SCRAPE_CACHE );
					}
				}
			}else{

				byte[] existing_bytes = (byte[]) attributes.get(attribute_name);
				byte[] new_bytes = attribute_value.getBytes(Constants.DEFAULT_ENCODING_CHARSET);

				if (existing_bytes == null || !Arrays.equals(existing_bytes, new_bytes)) {
					attributes.put(attribute_name, new_bytes);
					changed = true;
					if ( set_dirty ){
						setDirty( attribute_name == DownloadManagerState.AT_AGGREGATE_SCRAPE_CACHE );
					}
				}
			}
		}finally{

			this_mon.exit();
		}

		if ( changed ){

			informWritten( attribute_name );
		}
	}

	@Override
	public long
	getLongAttribute(
		String	attribute_name )
	{
		informWillRead( attribute_name );

		try{
			this_mon.enter();

			Long	l = (Long)attributes.get( attribute_name );

			if ( l == null ){

				Object def = default_attributes.get( attribute_name );

				if ( def != null ){

					if ( def instanceof Long ){

						return(((Long)def).longValue());

					}else if ( def instanceof Integer ){

						return(((Integer)def).longValue());

					}else{

						Debug.out( "unknown default type " + def );
					}
				}else if ( attribute_name == AT_FILES_EXPANDED ){

					boolean featured = TorrentUtils.isFeaturedContent( torrent );

					long res = featured?1:0;

					attributes.put( attribute_name, new Long( res ));

					setDirty( false );

					return( res );
				}

				return( 0 );
			}

			return( l.longValue());

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	setLongAttribute(
		final String	attribute_name,
		final long		attribute_value )
	{
		boolean	changed	= false;

		try{
			this_mon.enter();

			Long	existing_value = (Long)attributes.get( attribute_name );

			if ( 	existing_value == null ||
					existing_value.longValue() != attribute_value ){

				attributes.put( attribute_name, new Long( attribute_value) );

				changed = true;
					
				boolean	set_dirty = true;
				
				boolean is_scrape_cache = attribute_name == DownloadManagerState.AT_SCRAPE_CACHE ;
				
				if ( is_scrape_cache && download_manager.getGlobalManager().isStopping()){
					
					 if ( download_manager.getState() == DownloadManager.STATE_STOPPED ){
						 
						 set_dirty = false;
					 }
				}
				
				if ( set_dirty ){
				
					setDirty( is_scrape_cache );
				}
			}
		}finally{

			this_mon.exit();
		}

		if ( changed ){

			informWritten( attribute_name );
		}
	}

	@Override
	public void
	setListAttribute(
		String		name,
		String[]	values )
	{
		List	list = values==null?null:Arrays.asList((Object[]) values.clone());
		/*
		if ( list != null ){

			for (int i=0;i<values.length;i++){

				list.add( values[i]);
			}
		}*/

		setListAttribute( name, list );
	}

	@Override
	public String getListAttribute(String name, int idx) {
		if (name.equals(AT_NETWORKS) || name.equals(AT_PEER_SOURCES))
			throw new UnsupportedOperationException("not supported right now, implement it yourself :P");

		informWillRead(name);

		try {
			this_mon.enter();
			List values = (List) attributes.get(name);
			if(values == null || idx >= values.size() || idx < 0)
				return null;
			Object o = values.get(idx);
			if (o instanceof byte[]) {
				byte[] bytes = (byte[]) o;
				String s = StringInterner.intern(new String(bytes, Constants.DEFAULT_ENCODING_CHARSET));
				values.set(idx, s);
				return s;
			} else if (o instanceof String) {
				return (String) o;
			}
		} finally {
			this_mon.exit();
		}

		return null;
	}

	@Override
	public String[]
	getListAttribute(
		String	attribute_name )
	{
		if ( attribute_name == AT_NETWORKS ){

			return( getNetworks());

		}else if ( attribute_name == AT_PEER_SOURCES ){

			return( getPeerSources());

		}else{

			List	l = getListAttributeSupport( attribute_name );

			if ( l == null ){

				return( null );
			}

			String[]	res = new String[l.size()];

			try {
				res = (String[])l.toArray(res);
			} catch (ArrayStoreException e)
			{
				Debug.out( "getListAttribute( " + attribute_name + ") - object isnt String - " + e );

				return( null );
			}


			return( res );
		}
	}

	protected List
	getListAttributeSupport(
		String	attribute_name )
	{
		informWillRead( attribute_name );

		try{
			this_mon.enter();

			List	values = (List)attributes.get( attribute_name );

			List	res = new ArrayList(values != null ? values.size() : 0);

			if ( values != null ){

				for (int i=0;i<values.size();i++){

					Object	o = values.get(i);

					if ( o instanceof byte[] ){

						byte[]	bytes = (byte[])o;
						String s = StringInterner.intern(new String(bytes, Constants.DEFAULT_ENCODING_CHARSET));

						res.add(s);
						values.set(i, s);

					}else if ( o instanceof String ){

						res.add( o );
					}
				}
			}

			return( res );

		}finally{

			this_mon.exit();
		}
	}

	protected void
	setListAttribute(
		final String	attribute_name,
		final List		attribute_value )
	{
		boolean	changed	= false;

		try{
			this_mon.enter();

			if ( attribute_value == null ){

				if ( attributes.containsKey( attribute_name )){

					attributes.remove( attribute_name );

					changed = true;
					
					setDirty( false );
				}
			}else{

				List old_value = getListAttributeSupport( attribute_name );

				if ( old_value == null || old_value.size() != attribute_value.size()){

					attributes.put( attribute_name, attribute_value );

					changed = true;
					
					setDirty( false );

				}else{

					if ( old_value == attribute_value ){

						Debug.out( "setListAttribute: should clone?" );
					}

					changed = !BEncoder.listsAreIdentical( old_value, attribute_value );

					if ( changed ){

						setDirty( false );

						attributes.put( attribute_name, attribute_value );
					}
				}
			}
		}finally{

			this_mon.exit();
		}

		if ( changed ){

			informWritten( attribute_name );
		}
	}

	@Override
	public Map
	getMapAttribute(
		String	attribute_name )
	{
		informWillRead( attribute_name );

		try{
			this_mon.enter();

			Map	value = (Map)attributes.get( attribute_name );

			return( value );

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	setMapAttribute(
		final String	attribute_name,
		final Map		attribute_value )
	{
		setMapAttribute( attribute_name, attribute_value, false );
	}

	protected void
	setMapAttribute(
		final String	attribute_name,
		final Map		attribute_value,
		boolean			disable_change_notification )
	{
		boolean	changed	= false;

		try{
			this_mon.enter();

			if ( attribute_value == null ){

				if ( attributes.containsKey( attribute_name )){

					attributes.remove( attribute_name );

					changed = true;
					
					setDirty( false );
				}
			}else{

				Map old_value = getMapAttribute( attribute_name );

				if ( old_value == null || old_value.size() != attribute_value.size()){

					attributes.put( attribute_name, attribute_value );

					changed = true;
					
					setDirty( false );

				}else{

					if ( old_value == attribute_value ){

						Debug.out( "setMapAttribute: should clone?" );
					}

					changed = !BEncoder.mapsAreIdentical( old_value, attribute_value );

					if ( changed ){

						setDirty( false );

						attributes.put( attribute_name, attribute_value );
					}
				}
			}
		}finally{

			this_mon.exit();
		}

		if ( changed && !disable_change_notification ){

			informWritten( attribute_name );
		}
	}

	@Override
	public boolean
	hasAttribute(
		String name )
	{
		try{
			this_mon.enter();

			if ( attributes == null) {return false;}

			return attributes.containsKey(name);

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public void
	removeAttribute(
		String attribute_name )
	{
		boolean changed = false;
		
		try{

			this_mon.enter();

			if ( attributes != null ){

				if ( attributes.remove( attribute_name ) != null ){
					
					changed = true;
				}
			}
		}finally{

			this_mon.exit();
		}
		
		if ( changed ){

			informWritten( attribute_name );
		}
	}
	
	// These methods just use long attributes to store data into.

	@Override
	public void
	setIntAttribute(
		String 	name,
		int 	value)
	{
		setLongAttribute(name, value);
	}

	@Override
	public int
	getIntAttribute(
		String name )
	{
		return (int)getLongAttribute(name);
	}

	@Override
	public void
	setBooleanAttribute(
		String 		name,
		boolean 	value )
	{
		setLongAttribute(name, (value ? 1 : 0));
	}

	@Override
	public boolean
	getBooleanAttribute(
		String name )
	{
		return getLongAttribute(name) != 0;
	}


	public static DownloadManagerState
	getDownloadState(
		DownloadManager	dm )
	{
		return( new nullState(dm));
	}

	protected void
	informWritten(
		final String		attribute_name )
	{
			// don't make any of this async as the link management code for cache files etc
			// relies on callbacks here being synchronous...

		List<CopyOnWriteList<DownloadManagerStateAttributeListener>> list = new ArrayList<>();
		list.add(global_listeners_write_map_cow.get(attribute_name));
		list.add(global_listeners_write_map_cow.get(null));
		list.add(listeners_write_map_cow.get(attribute_name));
		list.add(listeners_write_map_cow.get(null));

		for (CopyOnWriteList<DownloadManagerStateAttributeListener> write_listeners : list) {
			if (write_listeners == null) {
				continue;
			}

			for ( DownloadManagerStateAttributeListener l: write_listeners.getList()){

				try{

					l.attributeEventOccurred(download_manager, attribute_name, DownloadManagerStateAttributeListener.WRITTEN);

				}catch (Throwable t){

					Debug.printStackTrace(t);
				}
			}
		}
	}

	protected void
	informWillRead(
		final String		attribute_name )
	{
			// avoid potential recursion will a will-be-read causing a write that then
			// causes a further will-be-read...

		List	will_be_read_list = (List)tls_wbr.get();

		if ( !will_be_read_list.contains( attribute_name )){

			will_be_read_list.add( attribute_name );

			try{

				List<CopyOnWriteList<DownloadManagerStateAttributeListener>> list = new ArrayList<>();
				list.add(global_listeners_read_map_cow.get(attribute_name));
				list.add(global_listeners_read_map_cow.get(null));
				list.add(listeners_read_map_cow.get(attribute_name));
				list.add(listeners_read_map_cow.get(null));

				for (CopyOnWriteList<DownloadManagerStateAttributeListener> read_listeners : list) {
					if (read_listeners == null) {
						continue;
					}

					for ( DownloadManagerStateAttributeListener l: read_listeners.getList()){

						try{
							l.attributeEventOccurred(download_manager, attribute_name, DownloadManagerStateAttributeListener.WILL_BE_READ);

						}catch( Throwable t ){

							Debug.printStackTrace(t);
						}
					}
				}
			}finally{

				will_be_read_list.remove( attribute_name );
			}
		}
	}

	/**
	 *
	 * @param l Listener to fire
	 * @param attribute attribute to listen for (null for all)
	 * @param event_type {@link DownloadManagerStateAttributeListener#WILL_BE_READ} or {@link DownloadManagerStateAttributeListener#WRITTEN}
	 */
	@Override
	public void addListener(DownloadManagerStateAttributeListener l, String attribute, int event_type) {
		CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> map_to_use = (event_type == DownloadManagerStateAttributeListener.WILL_BE_READ) ? this.listeners_read_map_cow : this.listeners_write_map_cow;
		CopyOnWriteList<DownloadManagerStateAttributeListener> lst = map_to_use.get(attribute);
		if (lst == null) {
			lst = new CopyOnWriteList<>();
			map_to_use.put(attribute, lst);
		}
		lst.add(l);
	}

	@Override
	public void removeListener(DownloadManagerStateAttributeListener l, String attribute, int event_type) {
		CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> map_to_use = (event_type == DownloadManagerStateAttributeListener.WILL_BE_READ) ? this.listeners_read_map_cow : this.listeners_write_map_cow;
		CopyOnWriteList<DownloadManagerStateAttributeListener> lst = map_to_use.get(attribute);
		if (lst != null) {lst.remove(l);}
	}

	public static void
	addGlobalListener(
		DownloadManagerStateAttributeListener l, String attribute, int event_type)
	{
		CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> map_to_use = (event_type == DownloadManagerStateAttributeListener.WILL_BE_READ) ? global_listeners_read_map_cow : global_listeners_write_map_cow;
		CopyOnWriteList<DownloadManagerStateAttributeListener> lst = map_to_use.get(attribute);
		if (lst == null) {
			lst = new CopyOnWriteList<>();
			map_to_use.put(attribute, lst);
		}
		lst.add(l);
	}

	public static void
	removeGlobalListener(
		DownloadManagerStateAttributeListener l, String attribute, int event_type)
	{
		CopyOnWriteMap<String,CopyOnWriteList<DownloadManagerStateAttributeListener>> map_to_use = (event_type == DownloadManagerStateAttributeListener.WILL_BE_READ) ? global_listeners_read_map_cow : global_listeners_write_map_cow;
		CopyOnWriteList<DownloadManagerStateAttributeListener> lst = (CopyOnWriteList)map_to_use.get(attribute);
		if (lst != null) {lst.remove(l);}
	}

	@Override
	public void
	generateEvidence(
		IndentWriter	writer,
		boolean			full )
	{
		writer.println( "DownloadManagerState" );

		try{
			writer.indent();

			writer.println( "parameters=" + parameters );
			writer.println( "flags=" + getFlags());
			DiskManagerFileInfo primaryFile = getPrimaryFile();
			if (primaryFile != null) {
				String prim = primaryFile.getFile(true).getAbsolutePath();
				if ( !full ){
					prim = Debug.secretFileName( prim );
				}
				writer.println("primary file=" + prim );
			}

		}finally{

			writer.exdent();
		}
	}


	@Override
	public void
	dump(
		IndentWriter writer)
	{
		writer.println( "attributes: " + parameters );
	}

	protected static class
	nullState
		implements DownloadManagerState
	{

		protected final DownloadManager		download_manager;

		protected
		nullState(
			DownloadManager	_dm )
		{
			download_manager = _dm;
		}

		@Override
		public TOTorrent
		getTorrent()
		{
			return( null );
		}

		@Override
		public File
		getStateFile( )
		{
			return( null );
		}

		public boolean
		getAndClearRecoveredStatus()
		{
			return( false );
		}
		
		@Override
		public DownloadManager
		getDownloadManager()
		{
			return( download_manager );
		}

		@Override
		public void
		clearResumeData()
		{
		}

		@Override
		public Map
		getResumeData()
		{
			return( new HashMap());
		}

		@Override
		public void
		setResumeData(
			Map	data )
		{
		}

		@Override
		public boolean
		isResumeDataComplete()
		{
			return( false );
		}

		@Override
		public List<ResumeHistory> 
		getResumeDataHistory()
		{
			return( Collections.emptyList());
		}
		
		@Override
		public void 
		restoreResumeData(
			ResumeHistory history)
		{
		}
		
		@Override
		public void
		clearTrackerResponseCache()
		{
		}

		@Override
		public Map
		getTrackerResponseCache()
		{
			return( new HashMap());
		}

		@Override
		public void
		setTrackerResponseCache(
			Map		value )
		{
		}

		@Override
		public void
		setFlag(
			long		flag,
			boolean		set )
		{
		}

		@Override
		public boolean
		getFlag(
			long		flag )
		{
			return( false );
		}

		@Override
		public long
		getFlags()
		{
			return 0;
		}

		public void
		setTransientFlag(
			long		flag,
			boolean		set )
		{
		}

		public boolean
		getTransientFlag(
			long		flag )
		{
			return( false );
		}

		public long
		getTransientFlags()
		{
			return( 0 );
		}
		
		public Object 
		getTransientAttribute(
			String name )
		{
			return( null );
		}
		
		@Override
		public void 
		setTransientAttribute(
			String 	name, 
			Object 	value)
		{
		}
		
		@Override
		public void
		setParameterDefault(
			String	name )
		{
		}

		@Override
		public long
		getLongParameter(
			String	name )
		{
			return( 0 );
		}

		@Override
		public void
		setLongParameter(
			String	name,
			long	value )
		{
		}

		@Override
		public int
		getIntParameter(
			String	name )
		{
			return( 0 );
		}

		@Override
		public void
		setIntParameter(
			String	name,
			int		value )
		{
		}

		@Override
		public boolean
		getBooleanParameter(
			String	name )
		{
			return( false );
		}

		@Override
		public void
		setBooleanParameter(
			String		name,
			boolean		value )
		{
		}

		@Override
		public void
		setAttribute(
			String		name,
			String		value )
		{
		}

		@Override
		public void
		setAttribute(
			String		name,
			String		value,
			boolean		setDirty )
		{
		}
		
		@Override
		public String
		getAttribute(
			String		name )
		{
			return( null );
		}

		@Override
		public String
		getTrackerClientExtensions()
		{
			return( null );
		}

		@Override
		public void
		setTrackerClientExtensions(
			String		value )
		{
		}

		@Override
		public void
		setListAttribute(
			String		name,
			String[]	values )
		{
		}


		@Override
		public String getListAttribute(String name, int idx) {
			return null;
		}

		@Override
		public String[]
		getListAttribute(
			String	name )
		{
			return( null );
		}

		@Override
		public void
		setMapAttribute(
			String		name,
			Map			value )
		{
		}

		@Override
		public Map
		getMapAttribute(
			String		name )
		{
			return( null );
		}

		@Override
		public boolean hasAttribute(String name) {return false;}
		@Override
		public void removeAttribute(String name){}
		@Override
		public int getIntAttribute(String name) {return 0;}
		@Override
		public long getLongAttribute(String name) {return 0L;}
		@Override
		public boolean getBooleanAttribute(String name) {return false;}
		@Override
		public void setIntAttribute(String name, int value) {}
		@Override
		public void setLongAttribute(String name, long value) {}
		@Override
		public void setBooleanAttribute(String name, boolean value) {}

		@Override
		public Category
		getCategory()
		{
			return( null );
		}

		@Override
		public void
		setCategory(
			Category cat )
		{
		}

		@Override
		public String[]
		getNetworks()
		{
			return( new String[0] );
		}


	    @Override
	    public boolean isNetworkEnabled(String network) {
	      return false;
	    }

		@Override
		public void
		setNetworks(
			String[]		networks )
		{
		}


	    @Override
	    public void setNetworkEnabled(
	        String network,
	        boolean enabled) {
	    }

		@Override
		public String[]
		getPeerSources()
		{
			return( new String[0] );
		}
		@Override
		public boolean
		isPeerSourcePermitted(
			String	peerSource )
		{
			return( false );
		}

		@Override
		public void setPeerSourcePermitted(String peerSource, boolean permitted) {
		}

	    @Override
	    public boolean
	    isPeerSourceEnabled(
	        String peerSource) {
	      return false;
	    }

	    @Override
	    public void suppressStateSave(boolean suppress) {}

		@Override
		public void
		setPeerSources(
			String[]		networks )
		{
		}


	    @Override
	    public void
	    setPeerSourceEnabled(
	        String source,
	        boolean enabled) {
	    }

	    @Override
	    public void
		setFileLink(
			int		source_index,
			File	link_source,
			File	link_destination )
	    {
	    }

	    @Override
	    public void
		setFileLinks(
			List<Integer>	source_indexes,
			List<File>		link_sources,
			List<File>		link_destinations )
	    {
	    }

		@Override
		public void
		clearFileLinks()
		{
		}

		@Override
		public File
		getFileLink(
			int		source_index,
			File	link_source )
		{
			return( null );
		}

		@Override
		public LinkFileMap
		getFileLinks()
		{
			return( new LinkFileMap());
		}

		@Override
		public int getFileFlags(int file_index){
			return 0;
		}

		@Override
		public void setFileFlags(int file_index, int flags){

		}
		    
		@Override
		public void
		setActive(boolean active )
		{
		}

		@Override
		public void discardFluff() {}

		@Override
		public boolean
		exportState(
			File	target_dir )
		{
			return( false );
		}

		@Override
		public void
		save( boolean interim )
		{
		}

		@Override
		public void
		delete()
		{
		}

		@Override
		public void addListener(DownloadManagerStateAttributeListener l, String attribute, int event_type) {}
		@Override
		public void removeListener(DownloadManagerStateAttributeListener l, String attribute, int event_type) {}

        @Override
        public void setDisplayName(String name) {}
        @Override
        public String getDisplayName() {return null;}

        @Override
        public void setUserComment(String name) {}
        @Override
        public String getUserComment() {return null;}

        public void setRelativeSavePath(String name) {}
        @Override
        public String getRelativeSavePath() {return null;}

		@Override
		public boolean parameterExists(String name) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void
		generateEvidence(
			IndentWriter 	writer,
			boolean			full )
		{
			writer.println( "DownloadManagerState: broken torrent" );
		}
		@Override
		public void
		dump(
			IndentWriter writer)
		{
		}

		@Override
		public boolean isOurContent() {
			// TODO Auto-generated method stub
			return false;
		}

		// @see com.biglybt.core.download.DownloadManagerState#getPrimaryFile()
		@Override
		public DiskManagerFileInfo getPrimaryFile() {
			// TODO Auto-generated method stub
			return null;
		}

		// @see com.biglybt.core.download.DownloadManagerState#setPrimaryFile(com.biglybt.core.disk.DiskManagerFileInfo)
		@Override
		public void setPrimaryFile(DiskManagerFileInfo dmfi) {
			// TODO Auto-generated method stub

		}
	}

	protected static class
	CachedStateWrapper
		extends 	LogRelation
		implements 	TorrentUtils.ExtendedTorrent
	{
		final DownloadManagerImpl	download_manager;

		final String		torrent_file;
		HashWrapper			torrent_hash_wrapper;
		Map					cache;
		Map					cache_attributes;
		Map					cache_azp;
		Map					cache_azpp;

		volatile TorrentUtils.ExtendedTorrent		delegate;
		TOTorrentException							fixup_failure;

		boolean		discard_pieces;
		boolean		logged_failure;

		Integer		torrent_type;
		Boolean		simple_torrent;
		long		size;
		Boolean		is_private;
		int			file_count;

		URL								announce_url;
		cacheGroup						announce_group;

		List<TOTorrentListener>			pending_listeners;
		
		volatile boolean		discard_fluff;

		protected
		CachedStateWrapper(
			DownloadManagerImpl		_download_manager,
			String					_torrent_file,
			byte[]					_torrent_hash,
			Map						_cache,
			boolean					_force_piece_discard )
		{
			download_manager		= _download_manager;
			torrent_file			= _torrent_file;
			torrent_hash_wrapper	= new HashWrapper( _torrent_hash );
			cache					= _cache;

			cache_attributes 	= (Map)cache.get( "attributes" );
			cache_azp 			= (Map)cache.get( "azp" );
			cache_azpp 			= (Map)cache.get( "azpp" );

			if ( _force_piece_discard ){

				discard_pieces	= true;

			}else{

				Long	l_fp = (Long)cache.get( "dp" );

				if ( l_fp != null ){

					discard_pieces = l_fp.longValue() == 1;
				}
			}

			Long	tt = (Long)cache.get( "tt" );

			if ( tt != null ){

				torrent_type = tt.intValue();
			}
			
			Long	st = (Long)cache.get( "simple" );

			if ( st != null ){

				simple_torrent = Boolean.valueOf(st.longValue() == 1);
			}

			Long	fc = (Long)cache.get( "fc" );

			if ( fc != null ){

				file_count = fc.intValue();
			}

			Long	l_size = (Long)cache.get( "size" );

			if ( l_size != null ){

				size = l_size.longValue();
			}

			Long	l_priv = (Long)cache.get( "priv" );

			if ( l_priv != null ){

				is_private = l_priv.longValue() != 0;
			}
			
			byte[]	au = (byte[])cache.get( "au" );

			if ( au != null ){

				try{
					announce_url = StringInterner.internURL(new URL((new String( au, "UTF-8" ))));

				}catch( Throwable e ){

				}
			}

			List	ag = (List)cache.get( "ag" );

			if ( ag != null ){

				try{
					announce_group = importGroup( ag );

				}catch( Throwable e ){

				}
			}
		}

		protected static Map
		export(
			DownloadManagerState dms )

			throws TOTorrentException
		{
			Map<String,Object>	cache = new HashMap<>();

			TOTorrent	state = dms.getTorrent();

			cache.put( "hash", state.getHash());
			cache.put( "name", state.getName());
			cache.put( "utf8name", state.getUTF8Name() == null ? "" : state.getUTF8Name());
			cache.put( "comment", state.getComment());
			cache.put( "createdby", state.getCreatedBy());
			cache.put( "size", new Long( state.getSize()));
			cache.put( "priv", new Long( state.getPrivate()?1:0));
			
			cache.put( "encoding", state.getAdditionalStringProperty( "encoding" ));
			cache.put( "torrent filename", state.getAdditionalStringProperty( "torrent filename" ));

			cache.put( "attributes", 	state.getAdditionalMapProperty( ATTRIBUTE_KEY ));
			cache.put( "azp", 			state.getAdditionalMapProperty( AZUREUS_PROPERTIES_KEY ));
			cache.put( "azpp", 			state.getAdditionalMapProperty( AZUREUS_PRIVATE_PROPERTIES_KEY ));

			try{
				cache.put( "au", state.getAnnounceURL().toExternalForm());
				cache.put( "ag", exportGroup(state.getAnnounceURLGroup()));

			}catch( Throwable e ){
			}

			boolean	discard_pieces = dms.isResumeDataComplete();

			TOTorrent	t = dms.getTorrent();

			if ( t instanceof CachedStateWrapper ){

					// We don't want to force a fixup here when saving!

				CachedStateWrapper csw = (CachedStateWrapper)t;

				if ( !discard_pieces ){

						// discard pieces if they are currently discarded

					discard_pieces = csw.peekPieces() == null;
				}

				Boolean	simple_torrent = csw.simple_torrent;

				if ( simple_torrent != null ){

					cache.put( "simple", new Long(simple_torrent.booleanValue()?1:0 ));

				}else{

					Debug.out( "Failed to cache simple state" );
				}

				int	fc = csw.file_count;

				if ( fc > 0 ){

					cache.put( "fc", new Long( fc ));
				}
				
				Integer	tt = csw.torrent_type;

				if ( tt != null ){

					cache.put( "tt", new Long( tt.intValue()));

				}else{

					Debug.out( "Failed to cache torrent type" );
				}
				
			}else{
				if ( t instanceof TorrentUtils.torrentDelegate ){

					// torrent is already 'fixed up' so no harm in directly grabbing stuff for export as already loaded

					cache.put( "simple", new Long(t.isSimpleTorrent()?1:0 ));

					cache.put( "fc", t.getFileCount());

					cache.put( "tt", new Long(t.getTorrentType()));
					
				}else{

					Debug.out( "Hmm, torrent isn't cache-state-wrapper, it is " + t );
				}
			}

			cache.put( "dp", new Long( discard_pieces?1:0 ));

			return( cache );
		}

		protected static List
		exportGroup(
			TOTorrentAnnounceURLGroup		group )
		{
			TOTorrentAnnounceURLSet[]	sets = group.getAnnounceURLSets();

			List	result = new ArrayList();

			for (int i=0;i<sets.length;i++){

				TOTorrentAnnounceURLSet	set = sets[i];

				URL[]	urls = set.getAnnounceURLs();

				if ( urls.length > 0 ){

					List	s = new ArrayList( urls.length );

					for (int j=0;j<urls.length;j++){

						s.add( urls[j].toExternalForm());
					}

					result.add( s );
				}
			}

			return( result );
		}

		protected cacheGroup
		importGroup(
			List		l )

			throws Exception
		{
			return( new cacheGroup( l ));
		}

		protected class
		cacheGroup
			implements TOTorrentAnnounceURLGroup
		{
			private TOTorrentAnnounceURLSet[]		sets;

			private volatile long	uid = TorrentUtils.getAnnounceGroupUID();
			
			protected
			cacheGroup(
				List	group )

				throws Exception
			{
				sets = new TOTorrentAnnounceURLSet[ group.size() ];

				for (int i = 0; i < sets.length; i++){

					List set = (List) group.get(i);

					URL[] urls = new URL[set.size()];

					for (int j = 0; j < urls.length; j++){

						urls[j] = StringInterner.internURL(new URL(new String((byte[]) set.get(j), "UTF-8")));
					}

					sets[i] = new cacheSet(urls);
				}
			}
			
			@Override
			public long 
			getUID()
			{
				TorrentUtils.ExtendedTorrent	del = delegate;
				
				if ( del != null ){
					
					return( del.getAnnounceURLGroup().getUID());
				}
				
				return( uid );
			}

			@Override
			public TOTorrentAnnounceURLSet[]
           	getAnnounceURLSets()
			{
				if ( announce_group == null && fixup()){

					return delegate.getAnnounceURLGroup().getAnnounceURLSets();
				}

				return( sets );
			}

			void fixGroup()
			{
				TOTorrentAnnounceURLSet[] realSets = delegate.getAnnounceURLGroup().getAnnounceURLSets();

				if ( realSets.length != sets.length ){

					// not a major issue - since we now allow the cached groups and persisted ones to
					// drift (see DNS changes) the cached ones will be accurate and the persisted ones
					// will be re-adjusted should they become visible
					// Debug.out("Cached announce group state does not match real state");

				}else{

					for (int i=0;i<realSets.length;i++){

						if ( sets[i] instanceof cacheSet ){

							((cacheSet)sets[i]).delegateSet = realSets[i];
						}
					}

					sets = null;
				}
			}

           	@Override
            public void
           	setAnnounceURLSets(
           		TOTorrentAnnounceURLSet[]	toSet )
           	{
     	   		if ( fixup()){

     	   			TOTorrentAnnounceURLSet[] modToSet = new TOTorrentAnnounceURLSet[toSet.length];

               		for (int i = 0; i < toSet.length; i++){

						TOTorrentAnnounceURLSet set = toSet[i];

						if ( set instanceof cacheSet ){

							modToSet[i] = ((cacheSet) set).delegateSet;
						}

						if ( modToSet[i] == null ){

							modToSet[i] = set;
						}
					}

    				delegate.getAnnounceURLGroup().setAnnounceURLSets( modToSet );
    			}
           	}

           	@Override
            public TOTorrentAnnounceURLSet
           	createAnnounceURLSet(
           		URL[]	urls )
           	{
           		if ( fixup()){

    				return( delegate.getAnnounceURLGroup().createAnnounceURLSet( urls ));
    			}

           		return( null );
           	}

           	protected class
           	cacheSet
           		implements TOTorrentAnnounceURLSet
           	{
           		private URL[]					urls;
           		TOTorrentAnnounceURLSet delegateSet;

				public cacheSet(URL[] urls)
				{
					this.urls = urls;
				}

           		@Override
	            public URL[]
           		getAnnounceURLs()
           		{
           			if ( announce_group == null && fixup() && delegateSet != null ){

           				return delegateSet.getAnnounceURLs();
           			}

           			return( urls );
           		}

		    	@Override
			    public void
		    	setAnnounceURLs(
		    		URL[]		toSet )
		    	{
		    		if ( fixup() && delegateSet != null ){

		    			delegateSet.setAnnounceURLs( toSet );

		    		}else{

		    			urls = toSet;
		    			
		    			uid = TorrentUtils.getAnnounceGroupUID();
		    		}
		    	}
           	}
		}

		protected void
		clearCache()
		{
			cache	= null;
		}

		protected boolean
		fixup()
		{
			try{
				if ( delegate == null ){

					synchronized( this ){

						if ( delegate == null ){

							// System.out.println( "Fixing up " + this );

							if ( fixup_failure != null ){

								throw( fixup_failure );
							}

							delegate = loadRealState();

							if ( discard_fluff ){

								delegate.setDiscardFluff( discard_fluff );
							}

							if ( cache != null ){

								Debug.out( "Cache miss forced fixup" );
							}

							cache = null;

								// join cache view back up with real state to save memory as the one
								// we've just read is irrelevant due to the cache values being
								// used

							if ( cache_attributes != null ){

								delegate.setAdditionalMapProperty( ATTRIBUTE_KEY, cache_attributes );

								cache_attributes = null;
							}

							if ( cache_azp != null ){

								delegate.setAdditionalMapProperty( AZUREUS_PROPERTIES_KEY, cache_azp );

								cache_azp = null;
							}
							
							if ( cache_azpp != null ){

								delegate.setAdditionalMapProperty( AZUREUS_PRIVATE_PROPERTIES_KEY, cache_azpp );

								cache_azpp = null;
							}

							announce_url = null;

							if ( announce_group != null ){

								announce_group.fixGroup();

								announce_group = null;
							}
							
							if ( pending_listeners != null ){
								for ( TOTorrentListener l: pending_listeners ){
									delegate.addListener( l );
								}
								pending_listeners = null;
							}
						}
					}
				}

				return( true );

			}catch( TOTorrentException e ){

				fixup_failure	= e;

				if ( download_manager != null ){

					download_manager.setTorrentInvalid( e );

				}else{

					if ( !logged_failure ){

						logged_failure = true;

						Debug.out( "Torrent can't be loaded: " + Debug.getNestedExceptionMessage( e ));
					}
				}
			}

			return( false );
		}

		protected TorrentUtils.ExtendedTorrent
		loadRealState()

			throws TOTorrentException
		{
			// System.out.println("loadReal: " + torrent_file + " dp=" + discard_pieces + ": " + Debug.getCompressedStackTrace().substring(114));

			if ( !SUPPRESS_FIXUP_ERRORS && Constants.isCVSVersion() ){

				if ( Thread.currentThread().isDaemon()){

					// Debug.outNoStack( "Fixup on thread " + Thread.currentThread().getName() + ": " + Debug.getCompressedStackTrace());

				}else{

					Debug.outNoStack( Debug.getCompressedStackTrace( new Exception(){public String toString(){ return( "Premature fixup?" );}}, 2, 10, true ), true);
				}
			}

			File	saved_file = getStateFile( torrent_hash_wrapper.getBytes() );

			if ( saved_file.exists()){

				try{

					return( TorrentUtils.readDelegateFromFile( saved_file, discard_pieces ));

				}catch( Throwable e ){

					Debug.out( "Failed to load download state for " + saved_file );
				}
			}

				// try reading from original

			TOTorrent original_torrent = TorrentUtils.readFromFile( FileUtil.newFile(torrent_file), true );

			torrent_hash_wrapper = original_torrent.getHashWrapper();

			saved_file = getStateFile( torrent_hash_wrapper.getBytes());

			boolean	was_corrupt = false;
			
			if ( saved_file.exists()){

				try{
					return( TorrentUtils.readDelegateFromFile( saved_file, discard_pieces ));

				}catch( Throwable e ){

					was_corrupt = true;
					
					Debug.out( "Failed to load download state for " + saved_file );
				}
			}

				// we must copy the torrent as we want one independent from the
				// original (someone might still have references to the original
				// and do stuff like write it somewhere else which would screw us
				// up)

			copyTorrentToActive( original_torrent, saved_file, was_corrupt );

			if ( was_corrupt ){
				
				if ( download_manager != null ){
					
					download_manager.setFailed( "Recovered from original torrent" );
				}
			}
			
			return( TorrentUtils.readDelegateFromFile( saved_file, discard_pieces ));
		}


		@Override
		public byte[]
    	getName()
		{
			Map	c = cache;

			if ( c != null ){

				byte[] name = (byte[])c.get( "name" );
				if (name != null) {
					return name;
				}
			}

	   		if ( fixup()){

				return( delegate.getName());
			}

	   		// Does grabbing the nested exception message always give us something useful?
	   		// My experience is that we just get an empty string here...
	   		return(("Error - " + Debug.getNestedExceptionMessage( fixup_failure )).getBytes());
    	}

		@Override
		public String getUTF8Name() {
			Map	c = cache;

			if ( c != null ){

				byte[] name = (byte[])c.get( "utf8name" );
				if (name != null) {
					String utf8name;
					try {
						utf8name = new String(name, "utf8");
					} catch (UnsupportedEncodingException e) {
						return null;
					}
					if (utf8name.length() == 0) {
						return null;
					}
					return utf8name;
				}
			}

			if (fixup()) {
				return delegate.getUTF8Name();
			}
			return null;
		}

	  	@Override
	    public int
    	getTorrentType()
    	{
    		if ( torrent_type != null ){

    			return( torrent_type );
    		}

    		if ( fixup()){

    			int tt = delegate.getTorrentType();

    			torrent_type = tt;

    			return( tt );
    		}

    		return( TOTorrent.TT_V1 );
    	}
	  	
		@Override
		public boolean 
		isExportable()
		{
			if ( fixup()){

				return( delegate.isExportable());
			}
			
			return( false );
		}
	  	
		@Override
		public boolean 
		updateExportability(
			TOTorrent from )
		{
			if ( fixup()){

				return( delegate.updateExportability( from ));
			}
			
			return( false );
		}
		
    	@Override
	    public boolean
    	isSimpleTorrent()
    	{
    		if ( simple_torrent != null ){

    			return( simple_torrent.booleanValue());
    		}

    		if ( fixup()){

    			boolean st = delegate.isSimpleTorrent();

    			simple_torrent = Boolean.valueOf(st);

    			return( st );
    		}

    		return( false );
    	}

    	@Override
	    public byte[]
    	getComment()
    	{
			Map	c = cache;

			if ( c != null ){

				return((byte[])c.get( "comment" ));
			}

	   		if ( fixup()){

				return( delegate.getComment());
			}

	   		return( null );
    	}

    	@Override
	    public void
    	setComment(
    		String		comment )
       	{
	   		if ( fixup()){

				delegate.setComment( comment );
			}
    	}

    	@Override
	    public long
    	getCreationDate()
       	{
	   		if ( fixup()){

				return( delegate.getCreationDate());
			}

	   		return( 0 );
    	}

    	@Override
	    public void
    	setCreationDate(
    		long		date )
       	{
	   		if ( fixup()){

				delegate.setCreationDate( date );
			}
    	}

    	@Override
	    public byte[]
    	getCreatedBy()
       	{
			Map	c = cache;

			if ( c != null ){

				return((byte[])c.get( "createdby" ));
			}

	   		if ( fixup()){

				return( delegate.getCreatedBy());
			}

	   		return( null );
    	}

       	@Override
        public void
    	setCreatedBy(
    		byte[]		cb )
       	{
	   		if ( fixup()){

				delegate.setCreatedBy( cb );
			}
    	}

    	@Override
	    public boolean
    	isCreated()
       	{
	   		if ( fixup()){

				return( delegate.isCreated());
			}

	   		return( false );
    	}

    	@Override
	    public boolean
    	isDecentralised()
    	{
    		return( TorrentUtils.isDecentralised( getAnnounceURL()));
    	}

    	@Override
	    public URL
    	getAnnounceURL()
       	{
    		if ( announce_url != null ){

    			return( announce_url );
    		}

	   		if ( fixup()){

				return( delegate.getAnnounceURL());
			}

	   		return( null );
    	}

    	@Override
	    public boolean
    	setAnnounceURL(
    		URL		url )
       	{
    		if ( announce_url != null ){

    			if ( announce_url.toExternalForm().equals( url.toExternalForm())){

    				return( false );
    			}
    		}

	   		if ( fixup()){

				return( delegate.setAnnounceURL( url ));

			}else{

				announce_url = url;
			}

	   		return( false );
    	}

    	@Override
	    public TOTorrentAnnounceURLGroup
    	getAnnounceURLGroup()
       	{
    		if ( announce_group != null ){

    			return( announce_group );
    		}

	   		if ( fixup()){

				return( delegate.getAnnounceURLGroup());
			}

	   		return( null );
    	}

    	@Override
	    public byte[][]
    	getPieces()

    		throws TOTorrentException
	   	{
	   		if ( fixup()){

				return( delegate.getPieces());
			}

	   		throw( fixup_failure );
    	}


    	@Override
	    public void
    	setPieces(
    		byte[][]	pieces )

    		throws TOTorrentException
	   	{
	   		if ( fixup()){

				delegate.setPieces( pieces );

				return;
			}

	   		throw( fixup_failure );
    	}

    	@Override
	    public byte[][]
    	peekPieces()

    		throws TOTorrentException
    	{
    		if ( fixup()){

    			return( delegate.peekPieces());
    		}

	   		throw( fixup_failure );
    	}

    	@Override
	    public void
    	setDiscardFluff(
    		boolean discard )
    	{
    		discard_fluff	= discard;

    		if ( delegate != null ){

    			delegate.setDiscardFluff( discard_fluff );
    		}
     	}

    	@Override
	    public long
    	getPieceLength()
       	{
	   		if ( fixup()){

				return( delegate.getPieceLength());
			}

	   		return( 0 );
       	}

		@Override
		public int
    	getNumberOfPieces()
       	{
	   		if ( fixup()){

				return( delegate.getNumberOfPieces());
			}

	   		return( 0 );
    	}

    	@Override
	    public long
    	getSize()
       	{
    		if ( size > 0 ){

    			return( size );
    		}

	   		if ( fixup()){

				size = delegate.getSize();

				return( size );
			}

	   		return( 0 );
    	}

       	@Override
        public int
    	getFileCount()
       	{
       		if ( file_count == 0 ){

       			if ( fixup()){

       				file_count= delegate.getFileCount();
       			}
       		}

       		return( file_count );
       	}

    	@Override
	    public TOTorrentFile[]
    	getFiles()
       	{
	   		if ( fixup()){

				return( delegate.getFiles());
			}

	   		return( new TOTorrentFile[0] );
    	}

    	@Override
	    public byte[]
    	getHash()

    		throws TOTorrentException
	   	{
    			// optimise this

    		return( torrent_hash_wrapper.getBytes());
    	}
    	
    	@Override
    	public byte[]
    	getFullHash(
    		int		type ) 
    	
    		throws TOTorrentException
    	{
   			if ( fixup()){

   				return( delegate.getFullHash( type ));
   				
   			}else{
    				
   				return( null );
   			}
    	}

    	@Override
	    public HashWrapper
    	getHashWrapper()

    		throws TOTorrentException
	   	{
    		return( torrent_hash_wrapper );
    	}
    	
    	@Override
    	public TOTorrent 
    	selectHybridHashType(
    		int type ) 
    		
    		throws TOTorrentException
    	{
    		if ( fixup()){
    			
    			return( delegate.selectHybridHashType(type));
    			
    		}else{
    			
    			throw( new TOTorrentException( "fixup failed", TOTorrentException.RT_CREATE_FAILED ));
    		}
    	}
    	
    	@Override
	    public void
    	setHashOverride(
    		byte[] hash )

    		throws TOTorrentException
    	{
    		throw( new TOTorrentException( "Not supported", TOTorrentException.RT_HASH_FAILS ));
    	}

    	@Override
    	public TOTorrent
    	setSimpleTorrentDisabled(
    		boolean	disabled )
    	
    		throws TOTorrentException
    	{
    		if ( fixup()){
    			
    			return( delegate.setSimpleTorrentDisabled( disabled ));
    			
    		}else{
    			
    			throw( new TOTorrentException( "fixup failed", TOTorrentException.RT_CREATE_FAILED ));
    		}	
    	}
    	
    	@Override
    	public boolean
    	isSimpleTorrentDisabled()
    	
    		throws TOTorrentException
    	{
    		if ( fixup()){
    			
    			return( delegate.isSimpleTorrentDisabled());
    			
    		}else{
    			
    			throw( new TOTorrentException( "fixup failed", TOTorrentException.RT_CREATE_FAILED ));
    		}
    	}
    	
    	@Override
	    public boolean
    	hasSameHashAs(
    		TOTorrent		other )
       	{
    		try{
    			byte[]	other_hash = other.getHash();

    			return( Arrays.equals( getHash(), other_hash ));

    		}catch( TOTorrentException e ){

    			Debug.printStackTrace( e );

    			return( false );
    		}
       	}

    	@Override
	    public boolean
    	getPrivate()
       	{
    		if ( is_private == null ){
    			
		   		if ( fixup()){
	
					return( delegate.getPrivate());
				}
	
		   		return( false );
		   		
    		}else{
    			
    			return( is_private );
    		}
    	}

    	@Override
	    public void
    	setPrivate(
    		boolean	_private )

    		throws TOTorrentException
	   	{
    		is_private = null;
    		
	   		if ( fixup()){

				delegate.setPrivate( _private );
			}
    	}

    	@Override
	    public String
    	getSource()
       	{
	   		if ( fixup()){
	
				return( delegate.getSource());
			}
	   		
	   		return( null );
       	}

    	@Override
	    public void
    	setSource(
    		String	str )
    	
    		throws TOTorrentException
	   	{
  	   		if ( fixup()){

				delegate.setSource( str );
			}
    	}
    	
    	@Override
	    public void
    	setAdditionalStringProperty(
    		String		name,
    		String		value )
       	{
	   		if ( fixup()){

				delegate.setAdditionalStringProperty( name, value );
			}
    	}

    	@Override
	    public String
    	getAdditionalStringProperty(
    		String		name )
       	{
			Map	c = cache;

			if ( c != null && ( name.equals( "encoding") || name.equals( "torrent filename" ))){

				byte[] res = (byte[])c.get( name );

				if ( res == null ){

					return( null );
				}

				try{
					return( new String( res, "UTF8" ));

				}catch( Throwable e ){

					Debug.printStackTrace( e );

					return( null );
				}
			}

	   		if ( fixup()){

				return( delegate.getAdditionalStringProperty( name ));
			}

	   		return( null );
    	}

    	@Override
	    public void
    	setAdditionalByteArrayProperty(
    		String		name,
    		byte[]		value )
       	{
	   		if ( fixup()){

				delegate.setAdditionalByteArrayProperty( name, value );
			}
    	}

    	@Override
	    public byte[]
    	getAdditionalByteArrayProperty(
    		String		name )
       	{
	   		if ( fixup()){

				return( delegate.getAdditionalByteArrayProperty( name ));
			}

	   		return( null );
    	}

    	@Override
	    public void
    	setAdditionalLongProperty(
    		String		name,
    		Long		value )
       	{
	   		if ( fixup()){

				delegate.setAdditionalLongProperty( name, value );
			}
    	}

    	@Override
	    public Long
    	getAdditionalLongProperty(
    		String		name )
       	{
	   		if ( fixup()){

				return( delegate.getAdditionalLongProperty( name ));
			}

	   		return( null );
    	}


    	@Override
	    public void
    	setAdditionalListProperty(
    		String		name,
    		List		value )
       	{
	   		if ( fixup()){

				delegate.setAdditionalListProperty( name, value );
			}
    	}

    	@Override
	    public List
    	getAdditionalListProperty(
    		String		name )
       	{
	   		if ( fixup()){

				return( delegate.getAdditionalListProperty( name ));
			}

	   		return( null );
    	}

    	@Override
	    public void
    	setAdditionalMapProperty(
    		String		name,
    		Map			value )
       	{
	   		if ( fixup()){

				delegate.setAdditionalMapProperty( name, value );
			}
    	}

    	@Override
	    public Map
    	getAdditionalMapProperty(
    		String		name )
       	{
			Map	c = cache_attributes;

			if ( c != null &&  name.equals( ATTRIBUTE_KEY )){

				return( c );
			}

			c = cache_azp;

			if ( c != null &&  name.equals( AZUREUS_PROPERTIES_KEY )){

				return( c );
			}
			
			c = cache_azpp;

			if ( c != null &&  name.equals( AZUREUS_PRIVATE_PROPERTIES_KEY )){

				return( c );
			}


	   		if ( fixup()){

				return( delegate.getAdditionalMapProperty( name ));
			}

	   		return( null );
    	}

    	@Override
	    public Object
    	getAdditionalProperty(
    		String		name )
       	{
	   		if ( fixup()){

				return( delegate.getAdditionalProperty( name ));
			}

	   		return( null );
    	}

    	@Override
	    public void
    	setAdditionalProperty(
    		String		name,
    		Object		value )
       	{
	   		if ( fixup()){

				delegate.setAdditionalProperty( name, value );
			}
    	}

    	@Override
	    public void
    	removeAdditionalProperty(
    		String name )
       	{
	   		if ( fixup()){

				delegate.removeAdditionalProperty( name );
			}
    	}

    	@Override
	    public void
    	removeAdditionalProperties()
       	{
	   		if ( fixup()){

				delegate.removeAdditionalProperties();
			}
    	}

    	@Override
	    public void
    	serialiseToBEncodedFile(
    		File		file )

    		throws TOTorrentException
	   	{
	   		if ( fixup()){

				delegate.serialiseToBEncodedFile( file );

				return;
			}

	   		throw( fixup_failure );
    	}

    	@Override
	    public Map
    	serialiseToMap()

    		throws TOTorrentException
	   	{
	   		if ( fixup()){

				return( delegate.serialiseToMap());
			}

	   		throw( fixup_failure );
    	}

       @Override
       public void
       serialiseToXMLFile(
    	   File		file )

    	   throws TOTorrentException
   	   	{
   	   		if ( fixup()){

   				delegate.serialiseToXMLFile( file );

   				return;
   			}

   	   		throw( fixup_failure );
       	}

       @Override
       public void
       addListener(
    	  TOTorrentListener		l )
       {
    	   synchronized( this ){
    		   if ( delegate != null ){
    			   delegate.addListener( l );
    		   }else{
    			   if ( pending_listeners == null ){
    				   pending_listeners = new ArrayList<>(2);
    			   }
    			   pending_listeners.add( l );
    		   }
    	   }
       }

       @Override
       public void
       removeListener(
    	  TOTorrentListener		l )
       {
    	   synchronized( this ){
    		   if ( delegate != null ){
    			   delegate.removeListener( l );
    		   }else{
    			   if ( pending_listeners != null ){
    				   pending_listeners.remove( l );
    			   }
    		   }
    	   }
       }

       @Override
       public AEMonitor
       getMonitor()
      	{
	   		if ( fixup()){

				return( delegate.getMonitor());
			}

	   		return( null );
      	}

       @Override
       public void
       print()
      	{
	   		if ( fixup()){

				delegate.print();
			}
      	}

     	/* (non-Javadoc)
     	 * @see com.biglybt.core.logging.LogRelation#getLogRelationText()
     	 */
     	@Override
      public String getRelationText() {
     		return "Torrent: '" + new String(getName()) + "'";
     	}

     	/* (non-Javadoc)
     	 * @see com.biglybt.core.logging.LogRelation#queryForClass(java.lang.Class)
     	 */
     	@Override
      public Object[] getQueryableInterfaces() {
     		// yuck
     		try {
     			return new Object[] { CoreFactory.getSingleton()
     					.getGlobalManager().getDownloadManager(this) };
     		} catch (Exception e) {
     		}

     		return null;
     	}
	}
}