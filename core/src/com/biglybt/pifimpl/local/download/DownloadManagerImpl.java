/*
 * File    : DownloadManagerImpl.java
 * Created : 06-Jan-2004
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

package com.biglybt.pifimpl.local.download;

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.category.Category;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerInitialisationAdapter;
import com.biglybt.core.download.DownloadManagerStateFactory;
import com.biglybt.core.download.impl.DownloadManagerDefaultPaths;
import com.biglybt.core.download.impl.DownloadManagerMoveHandler;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerDownloadRemovalVetoException;
import com.biglybt.core.global.GlobalManagerDownloadWillBeRemovedListener;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.download.*;
import com.biglybt.pif.download.savelocation.DefaultSaveLocationManager;
import com.biglybt.pif.download.savelocation.SaveLocationManager;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentException;
import com.biglybt.pif.ui.UIManagerEvent;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.torrent.TorrentImpl;
import com.biglybt.pifimpl.local.ui.UIManagerImpl;


public class
DownloadManagerImpl
	implements com.biglybt.pif.download.DownloadManager, DownloadManagerInitialisationAdapter
{
	protected static DownloadManagerImpl	singleton;
	protected static AEMonitor				class_mon	= new AEMonitor( "DownloadManager:class");

	public static DownloadManagerImpl
	getSingleton(
		Core core )
	{
		try{
			class_mon.enter();

			if ( singleton == null ){

				singleton = new DownloadManagerImpl( core );
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}


	private final GlobalManager					global_manager;
	private final DownloadManagerStats			stats;
	private final DownloadEventNotifierImpl 	global_dl_notifier;
	private final TagManager					tag_manager;

	private List<DownloadManagerListener>					listeners		= new ArrayList<>();
	private CopyOnWriteList<DownloadWillBeAddedListener>	dwba_listeners	= new CopyOnWriteList<>();
	private AEMonitor		listeners_mon	= new AEMonitor( "DownloadManager:L");

	private Map<DownloadManager,DownloadImpl>	pending_dls		= new IdentityHashMap<>();
	private Map<DownloadManager,DownloadImpl>	download_map	= new IdentityHashMap<>();

	protected
	DownloadManagerImpl(
		Core _core )
	{

		global_manager	= _core.getGlobalManager();

		stats = new DownloadManagerStatsImpl( global_manager );

		global_dl_notifier = new DownloadEventNotifierImpl(this);

		tag_manager = TagManagerFactory.getTagManager();

		readStubConfig();

		global_manager.addListener(
			new GlobalManagerListener()
			{
				@Override
				public void
				downloadManagerAdded(
					DownloadManager	dm )
				{
					addDownloadManager( dm );
				}

				@Override
				public void
				downloadManagerRemoved(
					DownloadManager	dm )
				{
					List<DownloadManagerListener>	listeners_ref	= null;
					DownloadImpl					dl				= null;

					try{
						listeners_mon.enter();

						dl = download_map.remove( dm );

						if ( dl == null ){

							Debug.out( "DownloadManager:unknown manager removed");

						}else{

							dl.destroy();
						}
						
						if ( pending_dls.remove( dm ) != null ){
							
							Debug.out( "Download removed without being added" );
						}
						
						listeners_ref = listeners;

					}finally{

						listeners_mon.exit();
					}

					if ( dl != null ){

						for (int i=0;i<listeners_ref.size();i++){

							try{
								listeners_ref.get(i).downloadRemoved( dl );

							}catch( Throwable e ){

								Debug.out( e );
							}
						}
					}
				}

				@Override
				public void
				destroyInitiated()
				{
				}

				@Override
				public void
				destroyed()
				{
					synchronized( download_stubs ){

						if ( dirty_stubs ){

							writeStubConfig();
						}
					}
				}


                @Override
                public void seedingStatusChanged(boolean seeding_only_mode, boolean b ){
                  //TODO
                }
			});

		global_manager.addDownloadWillBeRemovedListener(
			new GlobalManagerDownloadWillBeRemovedListener()
			{
				@Override
				public void
				downloadWillBeRemoved(
					DownloadManager	dm,
					boolean remove_torrent,
					boolean remove_data )

					throws GlobalManagerDownloadRemovalVetoException
				{
					DownloadImpl	download = (DownloadImpl)download_map.get( dm );

					if ( download != null ){

						try{
							download.isRemovable();

						}catch( DownloadRemovalVetoException e ){

							throw( new GlobalManagerDownloadRemovalVetoException( e.getMessage(),e.isSilent()));
						}
					}
				}
			});
	}

	@Override
	public void
	addDownload(
		final File fileName )
	{
		UIManagerImpl.fireEvent( null, UIManagerEvent.ET_OPEN_TORRENT_VIA_FILE, fileName );
	}

	@Override
	public void
	addDownload(
		final URL	url)
	{
		addDownload(url,null,true,null);
	}

	@Override
	public void
	addDownload(
		URL		url,
		boolean	auto_download )

		throws DownloadException
	{
		addDownload(url,null,auto_download,null);
	}

	@Override
	public void
	addDownload(
		final URL	url,
		final URL 	referrer)
	{
		addDownload(url,referrer,true,null);
	}

	@Override
	public void
	addDownload(
		URL 		url,
		Map 		request_properties )
	{
		addDownload(url,null,true,request_properties);
	}

	public void
	addDownload(
		final URL	url,
		final URL 	referrer,
		boolean		auto_download,
		Map			request_properties )
	{
		UIManagerImpl.fireEvent( null, UIManagerEvent.ET_OPEN_TORRENT_VIA_URL, new Object[]{ url, referrer,
			Boolean.valueOf(auto_download), request_properties });
	}


	protected void
	addDownloadManager(
		DownloadManager	dm )
	{
		List<DownloadManagerListener>		listeners_ref 	= null;
		DownloadImpl						dl				= null;

		try{
			listeners_mon.enter();

			if ( download_map.get(dm) == null ){

				dl = pending_dls.remove( dm );

				if ( dl == null ){
					
						// this is normal case for downloads already in BiglyBT on startup
					
					dl = new DownloadImpl( this, dm);
				}

				download_map.put( dm, dl );

				listeners_ref = listeners;
			}
		}finally{

			listeners_mon.exit();
		}

		if ( dl != null ){

			for (int i=0;i<listeners_ref.size();i++){

				try{
					listeners_ref.get(i).downloadAdded( dl );

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
		}
	}

	@Override
	public Download
	addDownload(
		Torrent		torrent )

		throws DownloadException
	{
	    return( addDownload( torrent, null, null ));
	}

	@Override
	public Download
	addDownload(
		Torrent		torrent,
		File		torrent_file,
		File		data_location )

		throws DownloadException
	{
		return( addDownload( torrent, torrent_file, data_location, getInitialState()));
	}

	public Download
	addDownload(
		Torrent		torrent,
		File		torrent_file,
		File		data_location,
		int			initial_state )

		throws DownloadException
	{
		if ( torrent_file == null ){

		    String torrent_dir = null;

		    if( COConfigurationManager.getBooleanParameter("Save Torrent Files")){

		      try{

		      	torrent_dir = COConfigurationManager.getDirectoryParameter("General_sDefaultTorrent_Directory");

		      }catch(Exception egnore){}
		    }

		    if ( torrent_dir == null || torrent_dir.length() == 0 ){

		    	throw( new DownloadException("DownloadManager::addDownload: default torrent save directory must be configured" ));
		    }

		    torrent_file = FileUtil.newFile( torrent_dir, FileUtil.convertOSSpecificChars( torrent.getName(), false ) + ".torrent" );

		    try{
		    	torrent.writeToFile( torrent_file );

		    }catch( TorrentException e ){

		    	throw( new DownloadException("DownloadManager::addDownload: failed to write torrent to '" + torrent_file.toString() + "'", e ));
		    }
		}

		else {
			if (!torrent_file.exists()) {
				throw new DownloadException("DownloadManager::addDownload: torrent file does not exist - " + torrent_file.toString());
			}
			else if (!torrent_file.isFile()) {
				throw new DownloadException("DownloadManager::addDownload: torrent filepath given is not a file - " + torrent_file.toString());
			}
		}

		if ( data_location == null ){

		    String data_dir = COConfigurationManager.getStringParameter("Default save path");

		    if ( data_dir == null || data_dir.length() == 0 ){

		    	throw( new DownloadException("DownloadManager::addDownload: default data save directory must be configured" ));
		    }

		    data_location = FileUtil.newFile(data_dir);

		    FileUtil.mkdirs(data_location);
		}

		byte[] hash = null;
		try {
			hash = torrent.getHash();
		} catch (Exception e) { }

		boolean	for_seeding = torrent.isComplete();

		DownloadManager dm = global_manager.addDownloadManager(
				torrent_file.toString(), hash, data_location.toString(),
				initial_state, true, for_seeding, null );

		if ( dm == null ){

			throw( new DownloadException( "DownloadManager::addDownload - failed, download may already in the process of being added"));
		}

		addDownloadManager( dm );

		return( getDownload( dm ));
	}

	@Override
	public Download
	addDownloadStopped(
		Torrent		torrent,
		File		torrent_location,
		File		data_location )

		throws DownloadException
	{
		return( addDownload( torrent, torrent_location, data_location, DownloadManager.STATE_STOPPED ));
	}

	@Override
	public Download
	addNonPersistentDownload(
		Torrent		torrent,
		File		torrent_file,
		File		data_location )

		throws DownloadException
	{

		byte[] hash = null;
		try {
			hash = torrent.getHash();
		} catch (Exception e) { }

		DownloadManager dm = global_manager.addDownloadManager(
				torrent_file.toString(), hash, data_location.toString(),
				getInitialState(), false);

		if ( dm == null ){

			throw( new DownloadException( "DownloadManager::addDownload - failed"));
		}

		addDownloadManager( dm );

		return( getDownload( dm ));
	}

	@Override
	public Download
	addNonPersistentDownloadStopped(
		Torrent		torrent,
		File		torrent_file,
		File		data_location )

		throws DownloadException
	{

		byte[] hash = null;
		try {
			hash = torrent.getHash();
		} catch (Exception e) { }

		DownloadManager dm = global_manager.addDownloadManager(
				torrent_file.toString(), hash, data_location.toString(),
				DownloadManager.STATE_STOPPED, false);

		if ( dm == null ){

			throw( new DownloadException( "DownloadManager::addDownload - failed"));
		}

		addDownloadManager( dm );

		return( getDownload( dm ));
	}

	@Override
	public void
	clearNonPersistentDownloadState(
		byte[] hash)
	{
		global_manager.clearNonPersistentDownloadState( hash );
	}

	protected int
	getInitialState()
	{
	  	boolean	default_start_stopped = COConfigurationManager.getBooleanParameter( "Default Start Torrents Stopped" );

        return( default_start_stopped?DownloadManager.STATE_STOPPED:DownloadManager.STATE_WAITING);
	}

	protected DownloadImpl
	getDownload(
		DownloadManager	dm )

		throws DownloadException
	{
		DownloadImpl dl = null;

		try{
			listeners_mon.enter();

			dl = download_map.get(dm);

			if ( dl != null ){

				return( dl );
			}

			dl = pending_dls.get( dm );

		}finally{

			listeners_mon.exit();
		}

		if ( dl != null ){

			long	 now = SystemTime.getMonotonousTime();

				// give the dl a chance to complete initialisation and appear in the right place...

			while( true ){

				try{
					listeners_mon.enter();
					
					DownloadImpl dl2 = download_map.get(dm);
	
					if ( dl2 != null ){
	
						return( dl2 );
					}

				}finally{

					listeners_mon.exit();
				}
				
				if ( SystemTime.getMonotonousTime() - now > 5000 ){

					break;
				}

				try{
					Thread.sleep(100);

				}catch( Throwable e ){
				}
			}

			return( dl );
		}

		throw( new DownloadException("DownloadManager::getDownload: download not found"));
	}

	public static DownloadImpl[] getDownloadStatic(DownloadManager[] dm) {
		ArrayList res = new ArrayList(dm.length);
		for (int i=0; i<dm.length; i++) {
			try {res.add(getDownloadStatic(dm[i]));}
			catch (DownloadException de) {}
		}
		return (DownloadImpl[])res.toArray(new DownloadImpl[res.size()]);
	}

	/**
	 * Retrieve the plugin Downlaod object related to the DownloadManager
	 *
	 * @param dm DownloadManager to find
	 * @return plugin object
	 * @throws DownloadException
	 */
	public static DownloadImpl
	getDownloadStatic(
		DownloadManager	dm )

		throws DownloadException
	{
		if ( singleton != null ){

			return( singleton.getDownload( dm ));
		}

		throw( new DownloadException( "DownloadManager not initialised"));
	}

	public static Download
	getDownloadStatic(
		DiskManager	dm )

		throws DownloadException
	{
		if ( singleton != null ){

			return( singleton.getDownload( dm ));
		}

		throw( new DownloadException( "DownloadManager not initialised"));
	}

	public Download
	getDownload(
		DiskManager	dm )

		throws DownloadException
	{
		List<DownloadManager>	dls = global_manager.getDownloadManagers();

		for (int i=0;i<dls.size();i++){

			DownloadManager	man = dls.get(i);

			if ( man.getDiskManager() == dm ){

				return( getDownload( man.getTorrent()));
			}
		}

		return( null );
	}

	protected Download
	getDownload(
		TOTorrent	torrent )

		throws DownloadException
	{
		if ( torrent != null ){

			try{
				listeners_mon.enter();
				
				for ( Download dl: download_map.values()){
	
					TorrentImpl	t = (TorrentImpl)dl.getTorrent();
	
						// can be null if broken torrent
	
					if ( t == null ){
	
						continue;
					}
	
					if ( t.getTorrent().hasSameHashAs( torrent )){
	
						return( dl );
					}
				}
			}finally{
				
				listeners_mon.exit();
			}
		}

		throw( new DownloadException("DownloadManager::getDownload: download not found"));
	}

	public static Download
	getDownloadStatic(
		TOTorrent	torrent )

		throws DownloadException
	{
		if ( singleton != null ){

			return( singleton.getDownload( torrent ));
		}

		throw( new DownloadException( "DownloadManager not initialised"));
	}

	@Override
	public Download
	getDownload(
		Torrent		_torrent )
	{
		TorrentImpl	torrent = (TorrentImpl)_torrent;

		try{
			return( getDownload( torrent.getTorrent()));

		}catch( DownloadException e ){
		}

		return( null );
	}

	@Override
	public Download
	getDownload(
		byte[]	hash )
	{
		DownloadManager manager = global_manager.getDownloadManager(new HashWrapper(hash));
		if (manager != null) {
			try {
				return getDownload(manager);
			} catch (DownloadException e) {
			}
		}

		List	dls = global_manager.getDownloadManagers();

		for (int i=0;i<dls.size();i++){

			DownloadManager	man = (DownloadManager)dls.get(i);

				// torrent can be null if download manager torrent file read fails

			TOTorrent	torrent = man.getTorrent();

			if ( torrent != null ){

				try{
					if ( Arrays.equals( torrent.getHash(), hash )){

						return( getDownload( torrent ));
					}
					
						// support looking up by truncated v2 hash so that magnet downloads for hybrid torrents using just
						// the btmh urn at least find content in our library
					
					byte[] v2_trunc = torrent.getTruncatedHash( TOTorrent.TT_V2 );
					
					if ( v2_trunc != null ){
												
						if ( Arrays.equals( v2_trunc, hash )){

							return( getDownload( torrent ));
						}
					}
					
				}catch( DownloadException e ){

						// not found

				}catch( TOTorrentException e ){

					Debug.printStackTrace( e );
				}
			}
		}

		return( null );
	}

	@Override
	public Download[]
	getDownloads()
	{
			// we have to use the global manager's ordering as it
			// hold this

		List<DownloadManager> dms = global_manager.getDownloadManagers();

		Set<Download>	res_l;

		try{
			listeners_mon.enter();

			res_l = new LinkedHashSet<>();

			for (int i=0;i<dms.size();i++){

				DownloadImpl	dl = download_map.get( dms.get(i));

				if ( dl != null ){

					res_l.add( dl );
				}
			}
		}finally{

			listeners_mon.exit();
		}

		Download[]	res = new Download[res_l.size()];

		res_l.toArray( res );

		return( res );
	}

	@Override
	public Download[]
	getDownloads(
		boolean bSorted )
	{
		if ( bSorted ){

			return( getDownloads());
		}

		try{
			listeners_mon.enter();

			return( download_map.values().toArray( new Download[ download_map.size()]));

		}finally{

			listeners_mon.exit();
		}
	}

	@Override
	public void
	pauseDownloads()
	{
		global_manager.pauseDownloads();
	}

	@Override
	public boolean
	canPauseDownloads()
	{
		return global_manager.canPauseDownloads();
	}

	@Override
	public void
	resumeDownloads()
	{
		global_manager.resumeDownloads();
	}

	@Override
	public boolean
	canResumeDownloads()
	{
		return global_manager.canResumeDownloads();
	}

	@Override
	public void
	startAllDownloads()
	{
		global_manager.startAllDownloads();
	}

	@Override
	public void
	stopAllDownloads()
	{
		global_manager.stopAllDownloads();
	}

	@Override
	public DownloadManagerStats
	getStats()
	{
		return( stats );
	}

	@Override
	public boolean
	isSeedingOnly()
	{
		return( global_manager.isSeedingOnly());
	}

	@Override
	public void addListener(DownloadManagerListener l) {addListener(l, true);}

	@Override
	public void addListener(DownloadManagerListener l, boolean notify_of_current_downloads) {
		List<Download> downloads_copy = null;

		try {
			listeners_mon.enter();
			List<DownloadManagerListener> new_listeners = new ArrayList<>(listeners);
			new_listeners.add(l);
			listeners = new_listeners;
			if (notify_of_current_downloads) {
				downloads_copy = new ArrayList<>( download_map.values());
				// randomize list so that plugins triggering dlm-state fixups don't lock each other by doing everything in the same order
				Collections.shuffle(downloads_copy);
			}
		}
		finally {
			listeners_mon.exit();
		}

		if (downloads_copy != null) {
			for (int i = 0; i < downloads_copy.size(); i++) {
				try {l.downloadAdded( downloads_copy.get(i));}
				catch (Throwable e) {Debug.printStackTrace(e);}
			}
		}
	}

	@Override
	public void removeListener(DownloadManagerListener l) {removeListener(l, false);}

	@Override
	public void removeListener(DownloadManagerListener l, boolean notify_of_current_downloads) {
		List<Download> downloads_copy = null;

		try {
			listeners_mon.enter();
			List<DownloadManagerListener> new_listeners = new ArrayList<>(listeners);
			new_listeners.remove(l);
			listeners = new_listeners;
			if (notify_of_current_downloads) {
				downloads_copy = new ArrayList<>( download_map.values());
			}
		}
		finally {
			listeners_mon.exit();
		}

		if (downloads_copy != null) {
			for (int i = 0; i < downloads_copy.size(); i++) {
				try {l.downloadRemoved( downloads_copy.get(i));}
				catch (Throwable e) {Debug.printStackTrace(e);}
			}
		}

	}

	@Override
	public void
	initialised(
		DownloadManager		manager,
		boolean				for_seeding )
	{
		DownloadImpl	dl;

		try{
			listeners_mon.enter();

			dl = new DownloadImpl( this, manager );

			pending_dls.put( manager, dl );

		}finally{

			listeners_mon.exit();
		}

		Iterator<DownloadWillBeAddedListener>	it = dwba_listeners.iterator();

		while( it.hasNext()){

			try{
				it.next().initialised(dl);

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}
		}
	}

	@Override
	public int
	getActions()
	{
		// assumption is that plugin based download-will-be-added listeners might assign tags so
		// indicate this

		if ( dwba_listeners.size() > 0 ){

			return( ACT_ASSIGNS_TAGS );
		}

		return( ACT_NONE );
	}

	@Override
	public void
	addDownloadWillBeAddedListener(
		DownloadWillBeAddedListener		listener )
	{
		try{
			listeners_mon.enter();

			dwba_listeners.add( listener );

			if ( dwba_listeners.size() == 1 ){

				global_manager.addDownloadManagerInitialisationAdapter( this );
			}

		}finally{
			listeners_mon.exit();
		}
	}

	@Override
	public void
	removeDownloadWillBeAddedListener(
		DownloadWillBeAddedListener		listener )
	{
		try{
			listeners_mon.enter();

			dwba_listeners.remove( listener );

			if ( dwba_listeners.size() == 0 ){

				global_manager.removeDownloadManagerInitialisationAdapter( this );
			}

		}finally{
			listeners_mon.exit();
		}
	}

	@Override
	public DownloadEventNotifier getGlobalDownloadEventNotifier() {
		return this.global_dl_notifier;
	}

	@Override
	public void setSaveLocationManager(SaveLocationManager manager) {
		if (manager == null) {manager = getDefaultSaveLocationManager();}
		DownloadManagerMoveHandler.CURRENT_HANDLER = manager;
	}

	@Override
	public SaveLocationManager getSaveLocationManager() {
		return DownloadManagerMoveHandler.CURRENT_HANDLER;
	}

	@Override
	public DefaultSaveLocationManager getDefaultSaveLocationManager() {
		return DownloadManagerDefaultPaths.DEFAULT_HANDLER;
	}



		// stubbin it

	private static final String	STUB_CONFIG_FILE 				= "dlarchive.config";
	private static final File	ARCHIVE_DIR;

	static{

		ARCHIVE_DIR = FileUtil.getUserFile( "dlarchive" );

		if ( !ARCHIVE_DIR.exists()){

			FileUtil.mkdirs(ARCHIVE_DIR);
		}
	}

	private List<DownloadStubImpl>				download_stubs 		= new ArrayList<>();
	private ByteArrayHashMap<DownloadStubImpl>	download_stub_map 	= new ByteArrayHashMap<>();

	private CopyOnWriteList<DownloadStubListener>	download_stub_listeners = new CopyOnWriteList<>();

	private FrequencyLimitedDispatcher dirty_stub_dispatcher =
			new FrequencyLimitedDispatcher(
					new AERunnable()
					{
						@Override
						public void
						runSupport()
						{
							synchronized( download_stubs ){

								writeStubConfig();
							}
						}
					},
					10*1000 );

	private boolean dirty_stubs = false;


	private void
	readStubConfig()
	{
		if ( FileUtil.resilientConfigFileExists( STUB_CONFIG_FILE )){

			Map map = FileUtil.readResilientConfigFile( STUB_CONFIG_FILE );

			List<Map>	list = (List<Map>)map.get( "stubs" );

			if ( list != null ){

				for ( Map m: list ){

					DownloadStubImpl stub = new DownloadStubImpl( this, m );

					download_stubs.add( stub );

					download_stub_map.put( stub.getTorrentHash(), stub );
				}
			}
		}
	}

	private void
	writeStubConfig()
	{
		if ( download_stubs.size() == 0 ){

			FileUtil.deleteResilientConfigFile( STUB_CONFIG_FILE );

		}else{

			Map map = new HashMap();

			List	list = new ArrayList( download_stubs.size());

			map.put( "stubs", list );

			for ( DownloadStubImpl stub: download_stubs ){

				list.add( stub.exportToMap());
			}

			FileUtil.writeResilientConfigFile( STUB_CONFIG_FILE, map );
		}

		dirty_stubs = false;
	}

	public boolean
	canStubbify(
		DownloadImpl	download )
	{
		if ( download.getState() != Download.ST_STOPPED ){

			return( false );
		}

		if ( !download.isPersistent()){

			return( false );
		}

		if ( download.getTorrent() == null ){

			return( false );
		}

		if ( download.getFlag( Download.FLAG_LOW_NOISE ) || download.getFlag( Download.FLAG_METADATA_DOWNLOAD )){

			return( false );
		}

		if ( !download.isComplete( false )){

			return( false );
		}
		
		/* Although this would seem to be the better thing to do it hides the actual cause
		 * of the veto (e.g. tag prevents removal) and makes it hard for a user to diagnose the
		 * reason. Better to allow things to go ahead and then fail on the removal attempt where
		 * the user can be told of the cause.
		 
		try{
			return( download.canBeRemoved());
			
		}catch( Throwable e ){
			
			Debug.out( e );
			
			return( false );
		}
		*/
		
		return( true );
	}

	protected DownloadStub
	stubbify(
		DownloadImpl	download )

		throws DownloadException, DownloadRemovalVetoException
	{
		if ( !canStubbify( download )){

			throw( new DownloadException( "Download not in stubbifiable state" ));
		}

		DownloadManager	core_dm = PluginCoreUtils.unwrap( download );

		Map<String,Object> gm_data = global_manager.exportDownloadStateToMap( core_dm );

			// meh, gm assumes this map is always serialised + deserialised and doesn't expect
			// String values

		try{
			gm_data = BDecoder.decode( BEncoder.encode( gm_data ));

		}catch( IOException e ){

			Debug.out( e );
		}


		String[]	manual_tags = null;

		if ( tag_manager.isEnabled()){

			List<Tag> tag_list = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL ).getTagsForTaggable( core_dm );

			if ( tag_list != null && tag_list.size() > 0 ){

					// hack to remove the restored tag name here as auto-added during restore

				String restored_tag_name = MessageText.getString( "label.restored" );

				tag_list = new ArrayList<>(tag_list);

				Iterator<Tag> it = tag_list.iterator();

				while( it.hasNext()){

					Tag t = it.next();

					//if ( t.isTagAuto()[0]){	// ignore auto_add tags

						// it.remove(); decided to keep these as they can be useful documentation
						// we just skip them on restore

					if ( t.getTagName( true ).equals( restored_tag_name )){

						it.remove();
					}
				}

				if ( tag_list.size() > 0 ){

					manual_tags = new String[tag_list.size()];

					for ( int i=0;i<manual_tags.length;i++){

						manual_tags[i] = tag_list.get(i).getTagName( true );
					}

					Arrays.sort( manual_tags );
				}
			}
		}
		
		Category cat = core_dm.getDownloadState().getCategory();

		String category;
		
		if ( cat != null && cat.getType()==Category.TYPE_USER ){
			
			category = cat.getName();
			
		}else{
			
			category = null;
		}
		
		DownloadStubImpl stub = new DownloadStubImpl( this,	download, core_dm, manual_tags, category,  gm_data );

		try{
			informAdded( stub, true );

		}finally{

			stub.setStubbified();
		}

		boolean	added = false;

		try{
			core_dm.getDownloadState().exportState( ARCHIVE_DIR );

			String old_save_path = stub.getSavePath();
			
			download.remove( false, false );

				// might have a 'move on remove' action setup
			
			String save_path_after_remove = download.getSavePath();
			
			if ( !old_save_path.contentEquals( save_path_after_remove )){
				
				stub.setSavePath( save_path_after_remove );
			}
			
			synchronized( download_stubs ){

				download_stubs.add( stub );

				download_stub_map.put( stub.getTorrentHash(), stub );

				writeStubConfig();
			}

			added = true;

			informAdded( stub, false );

		}finally{

			if ( !added ){

					// inform that the 'will be added' failed

				informRemoved( stub, true );
			}
		}

		return( stub );
	}

	protected Download
	destubbify(
		DownloadStubImpl		stub )

		throws DownloadException
	{
		boolean	removed = false;

		informRemoved( stub, true );

		try{
			byte[] torrent_hash = stub.getTorrentHash();

			try{
				DownloadManagerStateFactory.importDownloadState( ARCHIVE_DIR, torrent_hash );

			}catch( Throwable e ){

				throw( new DownloadException( "Failed to import download state", e ));
			}

			DownloadManager core_dm = global_manager.importDownloadStateFromMap( stub.getGMMap());

			if ( core_dm == null ){

				try{
					DownloadManagerStateFactory.deleteDownloadState( torrent_hash, false );

				}catch( Throwable e ){

					Debug.out( e );
				}

				throw( new DownloadException( "Failed to add download" ));

			}else{

				try{
					DownloadManagerStateFactory.deleteDownloadState( ARCHIVE_DIR, torrent_hash );

				}catch( Throwable e ){

					Debug.out( e );
				}

				File existing_location = core_dm.getSaveLocation();
				
				String stub_location = stub.getSavePath();
				
				if ( stub_location != null && !stub_location.isEmpty()){
					
					File f_stub = FileUtil.newFile( stub_location );
					
					if ( !f_stub.equals( existing_location )){
						
						core_dm.setTorrentSaveDir( f_stub, true );
					}
				}
				
				try{
					
					File	torrent_file = FileUtil.newFile( core_dm.getTorrentFileName() );

					if ( !torrent_file.exists()){
						
						// user might have moved torrent file backup location, see if the
						// torrent exists there
						
						String save_dir = COConfigurationManager.getDirectoryParameter( ConfigKeys.File.SCFG_GENERAL_DEFAULT_TORRENT_DIRECTORY );

						if ( !save_dir.isEmpty()){
							
							File	test_file = FileUtil.newFile( save_dir, torrent_file.getName());

							if ( test_file.exists()){
								
								core_dm.setTorrentFileName( test_file.getAbsolutePath());
							}
						}
					}
				}catch( Throwable e ){
					
					Debug.out( e );
				}
				
				synchronized( download_stubs ){

					download_stubs.remove( stub );

					download_stub_map.remove( stub.getTorrentHash());

					writeStubConfig();
				}

				String[] manual_tags = stub.getManualTags();

				if ( manual_tags != null && tag_manager.isEnabled()){

					TagType tt = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL );

					for ( String name: manual_tags ){

						Tag tag = tt.getTag( name, true );

						if ( tag == null ){

							try{
								tag = tt.createTag( name, true );

							}catch( Throwable e ){

								Debug.out(e);
							}
						}

						if ( tag != null ){

							if ( !tag.isTagAuto()[0] ){
							
								tag.addTaggable( core_dm );
							}
						}
					}
				}

				removed = true;

				informRemoved( stub, false );

				return( PluginCoreUtils.wrap( core_dm ));
			}
		}finally{

			if ( !removed ){

					// inform that the 'will be removed' failed

				informAdded( stub, true );
			}
		}
	}

	protected void
	remove(
		DownloadStubImpl		stub )
	{
		boolean removed = false;

		informRemoved( stub, true );

		try{
			try{
				DownloadManagerStateFactory.deleteDownloadState( ARCHIVE_DIR, stub.getTorrentHash());

			}catch( Throwable e ){

				Debug.out( e );
			}

			synchronized( download_stubs ){

				download_stubs.remove( stub );

				download_stub_map.remove( stub.getTorrentHash());

				writeStubConfig();
			}

			removed = true;

			informRemoved( stub, false );

		}finally{

			if ( !removed ){

				informAdded( stub, true );
			}
		}
	}

	public static TOTorrent
	getStubTorrent(
		byte[]		hash  )
	{
		File torrent_file = FileUtil.newFile( ARCHIVE_DIR, ByteFormatter.encodeString( hash ) + ".dat" );

		if ( torrent_file.exists()){

			try{
				return( TOTorrentFactory.deserialiseFromBEncodedFile( torrent_file ));

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		return( null );
	}
	protected TOTorrent
	getTorrent(
		DownloadStubImpl		stub )
	{
		File torrent_file = FileUtil.newFile( ARCHIVE_DIR, ByteFormatter.encodeString( stub.getTorrentHash()) + ".dat" );

		if ( torrent_file.exists()){

			try{
				return( TOTorrentFactory.deserialiseFromBEncodedFile( torrent_file ));

			}catch( Throwable e ){

				Debug.out( e );
			}
		}

		return( null );
	}

	protected void
	updated(
		DownloadStubImpl		stub )
	{
		synchronized( download_stubs ){

			dirty_stubs = true;
		}

		dirty_stub_dispatcher.dispatch();
	}

	@Override
	public DownloadStub[]
	getDownloadStubs()
	{
		synchronized( download_stubs ){

			return( download_stubs.toArray( new DownloadStub[download_stubs.size()]));
		}
	}

	@Override
	public int
	getDownloadStubCount()
	{
		synchronized( download_stubs ){

			return( download_stubs.size());
		}
	}

	@Override
	public DownloadStub
	lookupDownloadStub(
		byte[] hash)
	{
		synchronized( download_stubs ){

			return( download_stub_map.get( hash ));
		}
	}

	private Set<DownloadStub>	informing_of_add = new HashSet<>();

	private void
	informAdded(
		DownloadStub			stub,
		final boolean			preparing )
	{
		synchronized( informing_of_add ){

			if ( informing_of_add.contains( stub )){

				Debug.out( "Already informing of addition, ignoring" );

				return;
			}

			informing_of_add.add( stub );
		}

		try{
			final List<DownloadStub>	list = new ArrayList<>();

			list.add( stub );

			for ( DownloadStubListener l: download_stub_listeners ){

				try{
					l.downloadStubEventOccurred(
							new DownloadStubEvent()
							{
								@Override
								public int
								getEventType()
								{
									return( preparing?DownloadStubEvent.DSE_STUB_WILL_BE_ADDED:DownloadStubEvent.DSE_STUB_ADDED );
								}

								@Override
								public List<DownloadStub>
								getDownloadStubs()
								{
									return( list );
								}
							});

				}catch( Throwable e ){

					Debug.out( e );
				}
			}
		}finally{

			synchronized( informing_of_add ){

				informing_of_add.remove( stub );
			}
		}
	}

	private void
	informRemoved(
		DownloadStub			stub,
		final boolean			preparing )
	{
		final List<DownloadStub>	list = new ArrayList<>();

		list.add( stub );

		for ( DownloadStubListener l: download_stub_listeners ){

			try{
				l.downloadStubEventOccurred(
						new DownloadStubEvent()
						{
							@Override
							public int
							getEventType()
							{
								return( preparing?DownloadStubEvent.DSE_STUB_WILL_BE_REMOVED:DownloadStubEvent.DSE_STUB_REMOVED );
							}

							@Override
							public List<DownloadStub>
							getDownloadStubs()
							{
								return( list );
							}
						});

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	@Override
	public void
	addDownloadStubListener(
		DownloadStubListener 	l,
		boolean 				inform_of_current )
	{
		download_stub_listeners.add( l );

		if ( inform_of_current ){

			final List<DownloadStub>	existing;

			synchronized( download_stubs ){

				existing = new ArrayList<DownloadStub>( download_stubs );
			}

			try{
				l.downloadStubEventOccurred(
					new DownloadStubEvent()
					{
						@Override
						public int
						getEventType()
						{
							return( DownloadStubEvent.DSE_STUB_ADDED );
						}

						@Override
						public List<DownloadStub>
						getDownloadStubs()
						{
							return( existing );
						}
					});

			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}

	@Override
	public void
	removeDownloadStubListener(
		DownloadStubListener 	l )
	{
		download_stub_listeners.remove( l );
	}
}
