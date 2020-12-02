/*
 * File    : ShareManagerImpl.java
 * Created : 30-Dec-2003
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

package com.biglybt.pifimpl.local.sharing;

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.torrent.*;
import com.biglybt.core.tracker.util.TRTrackerUtils;
import com.biglybt.core.util.*;
import com.biglybt.pif.sharing.*;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.torrent.TorrentException;
import com.biglybt.pif.torrent.TorrentManager;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.torrent.TorrentImpl;

public class
ShareManagerImpl
	implements ShareManager, TOTorrentProgressListener, ParameterListener, AEDiagnosticsEvidenceGenerator
{
	private static final LogIDs LOGID = LogIDs.PLUGIN;
	public static final String		TORRENT_STORE 		= "shares";
	public static final String		TORRENT_SUBSTORE	= "cache";

	public static final int			MAX_FILES_PER_DIR	= 1000;
	public static final int			MAX_DIRS			= 1000;

	protected static ShareManagerImpl	singleton;
	private static AEMonitor			class_mon	= new AEMonitor( "ShareManager:class" );

	private static boolean	persistent_shares;

	static{
		COConfigurationManager.addAndFireParameterListener(
			"Sharing Is Persistent",
			new ParameterListener() {

				@Override
				public void parameterChanged(String parameterName){

					persistent_shares = COConfigurationManager.getBooleanParameter( "Sharing Is Persistent" );
				}
			});
	}

	protected AEMonitor				this_mon	= new AEMonitor( "ShareManager" );

	protected TOTorrentCreator		to_creator;

	public static ShareManagerImpl
	getSingleton()

		throws ShareException
	{
		try{
			class_mon.enter();

			if ( singleton == null ){

				singleton = new ShareManagerImpl();
			}

			return( singleton );

		}finally{

			class_mon.exit();
		}
	}


	private volatile boolean	initialised;
	private volatile boolean	initialising;

	private File				share_dir;

	private URL[]				announce_urls;
	private ShareConfigImpl		config;

	private Map<String,ShareResourceImpl>	shares 		= new HashMap<>();

	private ByteArrayHashMap<ShareResource>	torrent_map	= new ByteArrayHashMap<>();
	
	private shareScanner		current_scanner;
	private boolean				scanning;

	private List<ShareManagerListener>				listeners	= new ArrayList<>();

	protected
	ShareManagerImpl()

		throws ShareException
	{
		COConfigurationManager.addListener(
			new COConfigurationListener()
			{
				@Override
				public void
				configurationSaved()
				{
					announce_urls	= null;
				}
			});

		AEDiagnostics.addWeakEvidenceGenerator( this );
	}

	@Override
	public void
	initialise()
		throws ShareException
	{
		try{
			this_mon.enter();

			if ( !initialised ){

				try{
					initialising	= true;

					initialised		= true;

					listeners.add(
						new ShareManagerListener(){
							
							@Override
							public void resourceModified(ShareResource old_resource, ShareResource new_resource){
							}
							
							@Override
							public void 
							resourceDeleted(
								ShareResource resource)
							{
								handleResource( resource, false );								
							}
							
							@Override
							public void 
							resourceAdded(
								ShareResource resource)
							{
								handleResource( resource, true );								
							}
							
							private void
							handleResource(
								ShareResource		resource,
								boolean				added )
							{
								try{
									Torrent torrent = null;
	
									int type = resource.getType();
									
									if ( type == ShareResource.ST_FILE ){
	
										ShareResourceFile	file_resource = (ShareResourceFile)resource;
	
										ShareItem	item = file_resource.getItem();
	
										torrent = item.getTorrent();
	
									}else if ( type == ShareResource.ST_DIR ){
	
										ShareResourceDir	dir_resource = (ShareResourceDir)resource;
	
										ShareItem	item = dir_resource.getItem();
	
										torrent = item.getTorrent();
									}
									
									if ( torrent != null ){
										
										synchronized( torrent_map ){
										
											if ( added ){
											
												torrent_map.put( torrent.getHash(), resource );
												
											}else{
												
												torrent_map.remove( torrent.getHash());
											}
										}
									}
								}catch( Throwable e ){
									
									Debug.out( e );
								}

							}
							@Override
							public void reportProgress(int percent_complete){
							}
							
							@Override
							public void reportCurrentTask(String task_description){
							}
						});
					
					share_dir = FileUtil.getUserFile( TORRENT_STORE );

					FileUtil.mkdirs(share_dir);

					config = new ShareConfigImpl();

					try{
						config.suspendSaving();

						config.loadConfig(this);

					}finally{

						Iterator<ShareResourceImpl> it = shares.values().iterator();

						while(it.hasNext()){

							ShareResourceImpl	resource = it.next();

							if ( resource.getType() == ShareResource.ST_DIR_CONTENTS ){

								for (int i=0;i<listeners.size();i++){

									try{

										listeners.get(i).resourceAdded( resource );

									}catch( Throwable e ){

										Debug.printStackTrace( e );
									}
								}
							}
						}

						config.resumeSaving();
					}

					readAZConfig();

				}finally{

					initialising	= false;

					new AEThread2( "ShareManager:initScan", true )
					{
						@Override
						public void
						run()
						{
							try{
								scanShares();

							}catch( Throwable e ){

								Debug.printStackTrace(e);
							}
						}
					}.start();
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public boolean
	isInitialising()
	{
		return( initialising );
	}

	protected void
	readAZConfig()
	{
		COConfigurationManager.addParameterListener( "Sharing Rescan Enable", this );

		readAZConfigSupport();
	}

	@Override
	public void
	parameterChanged(
		String	name )
	{
		readAZConfigSupport();
	}

	protected void
	readAZConfigSupport()
	{
		try{
			this_mon.enter();

			boolean	scan_enabled	= COConfigurationManager.getBooleanParameter( "Sharing Rescan Enable" );

			if ( !scan_enabled ){

				current_scanner	= null;

			}else if ( current_scanner == null ){

				current_scanner = new shareScanner();
			}

		}finally{

			this_mon.exit();
		}
	}

	protected ShareConfigImpl
	getShareConfig()
	{
		return( config );
	}

	protected void
	checkConsistency()

		throws ShareException
	{
			// copy set for iteration as consistency check can delete resource

		Iterator<ShareResourceImpl>	it;
		
		try{
			this_mon.enter();
			
			it = new HashSet<>(shares.values()).iterator();
			
		}finally{
			
			this_mon.exit();
		}

		while(it.hasNext()){

			ShareResourceImpl	resource = it.next();

			try{
				resource.checkConsistency();

			}catch( ShareException e ){

				Debug.printStackTrace(e);
			}
		}
	}

	protected void
	deserialiseResource(
		Map					map )
	{
		try{
			ShareResourceImpl	new_resource = null;

			int	type = ((Long)map.get("type")).intValue();

			if ( 	type == ShareResource.ST_FILE ||
					type == ShareResource.ST_DIR ){

				new_resource = ShareResourceFileOrDirImpl.deserialiseResource( this, map, type );

			}else{

				new_resource = ShareResourceDirContentsImpl.deserialiseResource( this, map );
			}

			if ( new_resource != null ){

					// monitor already held
				
				ShareResourceImpl	old_resource = shares.get(new_resource.getName());

				if ( old_resource != null ){

					old_resource.delete(true);
				}

				shares.put( new_resource.getName(), new_resource );

					// we delay the reporting of dir_contents until all recovery is complete so that
					// the resource reported is initialised correctly

				if ( type != ShareResource.ST_DIR_CONTENTS ){

					for (int i=0;i<listeners.size();i++){

						try{

							((ShareManagerListener)listeners.get(i)).resourceAdded( new_resource );

						}catch( Throwable e ){

							Debug.printStackTrace( e );
						}
					}
				}
			}
		}catch( Throwable e ){

			Debug.printStackTrace( e );
		}
	}

	protected String
	getNewTorrentLocation()

		throws ShareException
	{
		for (int i=1;i<=MAX_DIRS;i++){

			File	cache_dir = FileUtil.newFile(share_dir, TORRENT_SUBSTORE + i);

			if ( !cache_dir.exists()){

				FileUtil.mkdirs(cache_dir);
			}

			if ( cache_dir.listFiles().length < MAX_FILES_PER_DIR ){

				for (int j=0;j<MAX_FILES_PER_DIR;j++){

					long	file = RandomUtils.nextAbsoluteLong();

					File	file_name = FileUtil.newFile(cache_dir, file + ".torrent");

					if ( !file_name.exists()){

							// return path relative to cache_dir to save space

						return( TORRENT_SUBSTORE + i + File.separator + file + ".torrent" );
					}
				}
			}
		}

		throw( new ShareException( "ShareManager: Failed to allocate cache file"));
	}

	protected void
	writeTorrent(
		ShareItemImpl		item )

		throws ShareException
	{
		try{
			item.getTorrent().writeToFile( getTorrentFile(item ));

		}catch( TorrentException e ){

			throw( new ShareException( "ShareManager: Torrent write fails", e ));
		}
	}

	protected void
	readTorrent(
		ShareItemImpl		item )

		throws ShareException
	{
		try{
			TOTorrent torrent = TOTorrentFactory.deserialiseFromBEncodedFile( getTorrentFile(item ));

			item.setTorrent(new TorrentImpl(torrent));

		}catch( TOTorrentException e ){

			throw( new ShareException( "ShareManager: Torrent read fails", e ));
		}
	}

	protected void
	deleteTorrent(
		ShareItemImpl		item )
	{
		File	torrent_file = getTorrentFile(item);

		torrent_file.delete();
	}

	protected boolean
	torrentExists(
		ShareItemImpl		item )
	{
		return( getTorrentFile(item).exists());
	}

	protected File
	getTorrentFile(
		ShareItemImpl		item )
	{
		return( FileUtil.newFile(share_dir, item.getTorrentLocation()));
	}

	protected URL[]
	getAnnounceURLs()

		throws ShareException
	{
		if ( announce_urls == null ){

			String	protocol = COConfigurationManager.getStringParameter( "Sharing Protocol" );

			if ( protocol.equalsIgnoreCase( "DHT" )){

				announce_urls	= new URL[]{ TorrentUtils.getDecentralisedEmptyURL()};

			}else{

				URL[][]	tracker_url_sets = TRTrackerUtils.getAnnounceURLs();

				if ( tracker_url_sets.length == 0 ){

					throw( new ShareException( "ShareManager: Tracker must be configured"));
				}

				for (int i=0;i<tracker_url_sets.length;i++){

					URL[]	tracker_urls = tracker_url_sets[i];

					if ( tracker_urls[0].getProtocol().equalsIgnoreCase( protocol )){

						announce_urls = tracker_urls;

						break;
					}
				}

				if ( announce_urls == null ){

					throw( new ShareException( "ShareManager: Tracker must be configured for protocol '" + protocol + "'" ));
				}
			}
		}

		return( announce_urls );
	}

	protected boolean
	getAddHashes()
	{
		return( COConfigurationManager.getBooleanParameter( "Sharing Add Hashes" ));
	}

	@Override
	public ShareResource[]
	getShares()
	{
		try{
			this_mon.enter();
		
			ShareResource[]	res = new ShareResource[shares.size()];

			shares.values().toArray( res );

			return( res );
			
		}finally{
			
			this_mon.exit();
		}
	}

	@Override
	public int 
	getShareCount()
	{
		return( shares.size());
	}
	
	protected ShareResourceImpl
	getResource(
		File		file )

		throws ShareException
	{
		try{
			this_mon.enter();
			
			return((ShareResourceImpl)shares.get(file.getCanonicalFile().toString()));

		}catch( IOException e ){

			throw( new ShareException( "getCanonicalFile fails", e ));
			
		}finally{
			
			this_mon.exit();
		}
	}

	@Override
	public ShareResource
	getShare(
		File	file_or_dir )
	{
		try{
			return( getResource( file_or_dir ));

		}catch( ShareException e ){

			return( null );
		}
	}
	
	@Override
	public ShareResource 
	lookupShare(
		byte[] torrent_hash ) 
			
		throws ShareException
	{
		if ( !initialised ){
		
				// need to force this in order to populate the torrent map
			
			initialise();
		}
		
		synchronized( torrent_map ){
			
			return( torrent_map.get( torrent_hash ));
		}
	}
	
	private boolean
	getBooleanProperty(
		Map<String,String>	properties,
		String				name )
	{
		if ( properties == null ){

			return( false );
		}

		String	value = properties.get( name );

		if ( value == null ){

			return( false );
		}

		return( value.equalsIgnoreCase( "true" ));
	}

	@Override
	public ShareResourceFile
	addFile(
		File	file )

		throws ShareException, ShareResourceDeletionVetoException
	{
		return( addFile( file, null ));
	}

	@Override
	public ShareResourceFile
	addFile(
		File				file,
		Map<String,String>	properties )

		throws ShareException, ShareResourceDeletionVetoException
	{
		return( addFile( null, file, getBooleanProperty( properties, PR_PERSONAL ), properties ));
	}

	protected ShareResourceFile
	addFile(
		ShareResourceDirContentsImpl	parent,
		File							file,
		boolean							personal,
		Map<String,String>				properties )

		throws ShareException, ShareResourceDeletionVetoException
	{
		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "ShareManager: addFile '"
					+ file.toString() + "'"));

		try{
			return( (ShareResourceFile)addFileOrDir( parent, file, ShareResource.ST_FILE, personal, properties ));

		}catch( ShareException e ){

			reportError(e);

			throw(e);
		}
	}

	public ShareResourceFile
	getFile(
		File	file )

		throws ShareException
	{
		return( (ShareResourceFile)ShareResourceFileImpl.getResource( this, file ));
	}

	@Override
	public ShareResourceDir
	addDir(
		File				dir )

		throws ShareException, ShareResourceDeletionVetoException
	{
		return( addDir( dir, null ));
	}

	@Override
	public ShareResourceDir
	addDir(
		File				dir,
		Map<String,String>	properties )

		throws ShareException, ShareResourceDeletionVetoException
	{
		return( addDir( null, dir, getBooleanProperty( properties, PR_PERSONAL ), properties ));
	}

	public ShareResourceDir
	addDir(
		ShareResourceDirContentsImpl	parent,
		File							dir,
		boolean							personal,
		Map<String,String>				properties )

		throws ShareException, ShareResourceDeletionVetoException
	{
		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "ShareManager: addDir '" + dir.toString()
					+ "'"));

		try{
			this_mon.enter();

			return( (ShareResourceDir)addFileOrDir( parent, dir, ShareResource.ST_DIR, personal, properties ));

		}catch( ShareException e ){

			reportError(e);

			throw(e);

		}finally{

			this_mon.exit();
		}
	}

	public ShareResourceDir
	getDir(
		File	file )

		throws ShareException
	{
		return( (ShareResourceDir)ShareResourceDirImpl.getResource( this, file ));
	}

	protected ShareResource
	addFileOrDir(
		ShareResourceDirContentsImpl	parent,
		File							file,
		int								type,
		boolean							personal,
		Map<String,String>				properties )

		throws ShareException, ShareResourceDeletionVetoException
	{
		properties = setPropertyDefaults( properties );

		try{
			this_mon.enter();

			String	name = file.getCanonicalFile().toString();

			ShareResourceImpl	old_resource = shares.get(name);

			boolean	modified = old_resource != null;

			if ( modified ){

				if ( old_resource.isPersistent()){

					return( old_resource );
				}

				old_resource.delete( true, false );
			}

			ShareResourceImpl new_resource;

			if ( type == ShareResource.ST_FILE ){

				reportCurrentTask( "Adding file '" + name + "'");

				new_resource = new ShareResourceFileImpl( this, parent, file, personal, properties );

			}else{

				reportCurrentTask( "Adding dir '" + name + "'");

				new_resource = new ShareResourceDirImpl( this, parent, file, personal, properties );
			}

			shares.put(name, new_resource );

			config.saveConfig();

			for (int i=0;i<listeners.size();i++){

				try{

					if ( modified ){

						((ShareManagerListener)listeners.get(i)).resourceModified( old_resource, new_resource );

					}else{

						((ShareManagerListener)listeners.get(i)).resourceAdded( new_resource );
					}
				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}

			return( new_resource );

		}catch( IOException e ){

			throw( new ShareException( "getCanoncialFile fails", e ));

		}finally{

			this_mon.exit();
		}
	}

	@Override
	public ShareResourceDirContents
	addDirContents(
		File				dir,
		boolean				recursive )

		throws ShareException, ShareResourceDeletionVetoException
	{
		return( addDirContents( dir, recursive, null ));
	}

	@Override
	public ShareResourceDirContents
	addDirContents(
		File				dir,
		boolean				recursive,
		Map<String,String>	properties )

		throws ShareException, ShareResourceDeletionVetoException
	{
		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "ShareManager: addDirContents '"
					+ dir.toString() + "'"));

		properties = setPropertyDefaults( properties );

		try{
			this_mon.enter();

			String	name = dir.getCanonicalFile().toString();

			reportCurrentTask( "Adding dir contents '" + name + "', recursive = " + recursive );

			ShareResource	old_resource = (ShareResource)shares.get( name );

			if ( old_resource != null ){

				if ( old_resource.isPersistent() && old_resource instanceof ShareResourceDirContents ){

					return((ShareResourceDirContents)old_resource );
				}

				old_resource.delete( true );
			}

			ShareResourceDirContentsImpl new_resource = new ShareResourceDirContentsImpl( this, dir, recursive, getBooleanProperty( properties, PR_PERSONAL ), properties, true );

			shares.put( name, new_resource );

			config.saveConfig();

			for (int i=0;i<listeners.size();i++){

				try{

					((ShareManagerListener)listeners.get(i)).resourceAdded( new_resource );

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}

			return( new_resource );

		}catch( IOException e ){

			reportError(e);

			throw( new ShareException( "getCanoncialFile fails", e ));

		}catch( ShareException e ){

			reportError(e);

			throw(e);

		}finally{

			this_mon.exit();
		}
	}

	protected void
	delete(
		ShareResourceImpl	resource,
		boolean				fire_listeners )

		throws ShareException
	{
		if (Logger.isEnabled())
			Logger.log(new LogEvent(LOGID, "ShareManager: resource '"
					+ resource.getName() + "' deleted"));

		try{
			this_mon.enter();

			shares.remove(resource.getName());

			resource.deleteInternal();

			config.saveConfig();

			if ( fire_listeners ){

				for (int i=0;i<listeners.size();i++){

					try{

						((ShareManagerListener)listeners.get(i)).resourceDeleted( resource );

					}catch( Throwable e ){

						Debug.printStackTrace( e );
					}
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	scanShares()

		throws ShareException
	{
		try{
			this_mon.enter();

			if ( scanning ){

				return;
			}

			scanning = true;

		}finally{

			this_mon.exit();
		}

		try{
			if (Logger.isEnabled())
				Logger.log(new LogEvent(LOGID,
						"ShareManager: scanning resources for changes"));

			checkConsistency();

		}finally{

			try{
				this_mon.enter();

				scanning = false;

			}finally{

				this_mon.exit();
			}
		}
	}

		// bit of a hack this, but to do it properly would require extensive rework to decouple the
		// process of saying "share file" and then actually doing it

	protected  void
	setTorrentCreator(
		TOTorrentCreator	_to_creator )
	{
		to_creator	= _to_creator;
	}

	private Map<String,String>
	setPropertyDefaults(
		Map<String,String>		properties )
	{
		if ( persistent_shares ){

			if ( properties == null ){

				properties = new HashMap<>();
			}

			if ( !properties.containsKey( ShareManager.PR_PERSISTENT )){

				properties.put( ShareManager.PR_PERSISTENT, persistent_shares?"true":"false" );
			}
		}

		return( properties );
	}

	@Override
	public void
	cancelOperation()
	{
		TOTorrentCreator	temp = to_creator;

		if ( temp != null ){

			temp.cancel();
		}
	}

	@Override
	public void
	reportProgress(
		int		percent_complete )
	{
		for (int i=0;i<listeners.size();i++){

			try{

				((ShareManagerListener)listeners.get(i)).reportProgress( percent_complete );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	@Override
	public void
	reportCurrentTask(
		String	task_description )
	{
		for (int i=0;i<listeners.size();i++){

			try{

				((ShareManagerListener)listeners.get(i)).reportCurrentTask( task_description );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}
	}

	protected void
	reportError(
		Throwable e )
	{
		String	message = e.getMessage();

		if ( message != null ){

			reportCurrentTask( Debug.getNestedExceptionMessage(e));

		}else{

			reportCurrentTask( e.toString());
		}
	}

	@Override
	public void
	addListener(
		ShareManagerListener		l )
	{
		listeners.add(l);
	}

	@Override
	public void
	removeListener(
		ShareManagerListener		l )
	{
		listeners.remove(l);
	}

	@Override
	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "Shares" );

		try{
			writer.indent();

			ShareResource[]	shares = getShares();

			HashSet	share_map = new HashSet();

			for ( int i=0;i<shares.length;i++ ){

				ShareResource	share = shares[i];

				if ( share instanceof ShareResourceDirContents ){

					share_map.add( share );

				}else if ( share.getParent() != null ){

				}else{

					writer.println( getDebugName( share ));
				}
			}

			Iterator	it = share_map.iterator();

			// We don't need GlobalManager, so isCoreRunning isn't needed
			// Hopefully all the things we need are avail on core create
			if (!CoreFactory.isCoreAvailable()) {
				// could probably log some stuff below, but for now
				// be safe and lazy and just exit
				writer.println("No Core");
				return;
			}

			TorrentManager tm = PluginInitializer.getDefaultInterface().getTorrentManager();

			TorrentAttribute	category_attribute 	= tm.getAttribute( TorrentAttribute.TA_CATEGORY );
			TorrentAttribute	props_attribute 	= tm.getAttribute( TorrentAttribute.TA_SHARE_PROPERTIES );

			while( it.hasNext()){

				ShareResourceDirContents	root = (ShareResourceDirContents)it.next();

				String	cat 	= root.getAttribute( category_attribute );
				String	props 	= root.getAttribute( props_attribute );

				String	extra = cat==null?"":(",cat=" + cat );

				extra += props==null?"":(",props=" + props );

				extra += ",rec=" + root.isRecursive();

				writer.println( root.getName() + extra );

				generate( writer, root );
			}
		}finally{

			writer.exdent();
		}
	}

	protected void
	generate(
		IndentWriter				writer,
		ShareResourceDirContents	node )
	{
		try{
			writer.indent();

			ShareResource[]	kids = node.getChildren();

			for (int i=0;i<kids.length;i++){

				ShareResource	kid = kids[i];

				writer.println( getDebugName( kid ));

				if ( kid instanceof ShareResourceDirContents ){

					generate( writer, (ShareResourceDirContents)kid );
				}
			}
		}finally{

			writer.exdent();
		}
	}

	protected String
	getDebugName(
		ShareResource	_share )
	{
		Torrent	torrent = null;

		try{
			if ( _share instanceof ShareResourceFile ){

				ShareResourceFile share = (ShareResourceFile)_share;

				torrent = share.getItem().getTorrent();

			}else if ( _share instanceof ShareResourceDir ){

				ShareResourceDir share = (ShareResourceDir)_share;

				torrent = share.getItem().getTorrent();
			}
		}catch( Throwable e ){
		}

		if ( torrent == null ){

			return(	Debug.secretFileName( _share.getName()));

		}else{

			return( Debug.secretFileName( torrent.getName() ) + "/" + ByteFormatter.encodeString( torrent.getHash()));
		}
	}


	protected class
	shareScanner
	{
		protected
		shareScanner()
		{
			current_scanner	= this;

			new AEThread2( "ShareManager::scanner", true )
			{
				@Override
				public void
				run()
				{
					while( current_scanner == shareScanner.this ){

						try{

							int		scan_period		= COConfigurationManager.getIntParameter( "Sharing Rescan Period" );

							if ( scan_period < 1 ){

								scan_period	= 1;
							}

							Thread.sleep( scan_period * 1000 );

							if ( current_scanner == shareScanner.this ){

								scanShares();
							}

						}catch( Throwable e ){

							Debug.printStackTrace(e);
						}
					}
				}
			}.start();
		}
	}
	
	protected void
	configDirty()
	{
		try{
				// todo could batch these up...
			
			config.saveConfig();
			
		}catch( Throwable e ){
			
		}
	}
}
