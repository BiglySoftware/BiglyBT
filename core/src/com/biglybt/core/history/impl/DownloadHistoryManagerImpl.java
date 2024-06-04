/*
 * Created on Sep 6, 2016
 * Created by Paul Gardner
 *
 * Copyright 2016 Azureus Software, Inc.  All rights reserved.
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA.
 */


package com.biglybt.core.history.impl;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.biglybt.core.Core;
import com.biglybt.core.CoreComponent;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.*;
import com.biglybt.core.download.impl.DownloadManagerAdapter;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.history.DownloadHistory;
import com.biglybt.core.history.DownloadHistoryEvent;
import com.biglybt.core.history.DownloadHistoryListener;
import com.biglybt.core.history.DownloadHistoryManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.tag.TagUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import com.biglybt.net.magneturi.MagnetURIHandler;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.utils.search.SearchException;
import com.biglybt.pif.utils.search.SearchInstance;
import com.biglybt.pif.utils.search.SearchObserver;
import com.biglybt.pif.utils.search.SearchProvider;
import com.biglybt.pif.utils.search.SearchResult;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.utils.SearchMatcher;

public class
DownloadHistoryManagerImpl
	implements DownloadHistoryManager
{
	private static final String CONFIG_ENABLED	= "Download History Enabled";

	private static final String	CONFIG_ACTIVE_FILE	= "dlhistorya.config";
	private static final String	CONFIG_DEAD_FILE 	= "dlhistoryd.config";

	private static final String CONFIG_ACTIVE_SIZE	= "download.history.active.size";
	private static final String CONFIG_DEAD_SIZE	= "download.history.dead.size";

	private static final int UPDATE_TYPE_ACTIVE	= 0x01;
	private static final int UPDATE_TYPE_DEAD	= 0x10;
	private static final int UPDATE_TYPE_BOTH	= UPDATE_TYPE_ACTIVE | UPDATE_TYPE_DEAD;

	private static final Object	TAG_CACHE_KEY = new Object();
	
	private final Core core;

	private static final TagManager	tag_manager = TagManagerFactory.getTagManager();
	
	private final ListenerManager<DownloadHistoryListener>	listeners =
		ListenerManager.createAsyncManager(
			"DHM",
			new ListenerManagerDispatcher<DownloadHistoryListener>() {
				@Override
				public void
				dispatch(
					DownloadHistoryListener listener,
					int 					type,
					Object 					value )
				{
					listener.downloadHistoryEventOccurred((DownloadHistoryEvent)value );
				}
			});

	final Object	lock = new Object();

	private AtomicInteger	mutation_count = new AtomicInteger();
	
	private WeakReference<Map<Long,DownloadHistoryImpl>>		history_active	= new WeakReference<>(null);
	private WeakReference<Map<Long,DownloadHistoryImpl>>		history_dead	= new WeakReference<>(null);

	volatile int	active_history_size	= COConfigurationManager.getIntParameter( CONFIG_ACTIVE_SIZE , 0 );
	volatile int	dead_history_size	= COConfigurationManager.getIntParameter( CONFIG_DEAD_SIZE , 0 );

	private Map<Long,DownloadHistoryImpl>	active_dirty;
	private Map<Long,DownloadHistoryImpl>	dead_dirty;

	TimerEvent	write_pending_event;

	private long	active_load_time;
	private long	dead_load_time;

	private boolean	history_escaped = false;

	private final Map<Long,Long>	redownload_cache	= new HashMap<>();

	private volatile ByteArrayHashMap<long[]>	dates_cache;
	private volatile int						dates_cache_mutation_count;
	private volatile long						dates_cache_last_access;
	
	boolean	enabled;

	public
	DownloadHistoryManagerImpl()
	{
		core = CoreFactory.getSingleton();

		COConfigurationManager.addAndFireParameterListener(
				CONFIG_ENABLED,
				new ParameterListener() {

					private boolean	first_time = true;

					@Override
					public void parameterChanged(String name ){

						setEnabledSupport( COConfigurationManager.getBooleanParameter( name ), first_time );

						first_time = false;
					}
				});

		core.addLifecycleListener(
			new CoreLifecycleAdapter()
			{
				@Override
				public void
				componentCreated(
					Core core,
					CoreComponent component )
				{
					if ( component instanceof GlobalManager ){

						GlobalManager global_manager = (GlobalManager)component;

						global_manager.addListener(
							new GlobalManagerAdapter()
							{
								@Override
								public void
								downloadManagerAdded(
									DownloadManager	dm )
								{
									synchronized( lock ){

										if ( !( enabled && isMonitored(dm))){

											return;
										}

										Map<Long,DownloadHistoryImpl> active_history = getActiveHistory();

										DownloadHistoryImpl new_dh = new  DownloadHistoryImpl( active_history, dm );

										long	uid = new_dh.getUID();

										DownloadHistoryImpl old_dh = active_history.put( uid, new_dh );

										if ( old_dh != null ){

											historyUpdated( old_dh, DownloadHistoryEvent.DHE_HISTORY_REMOVED, UPDATE_TYPE_ACTIVE );
										}

											// could be an archive-restore in which case the entry might exist in the dead records

										old_dh = getDeadHistory().remove( uid );

										if ( old_dh != null ){

											historyUpdated( old_dh, DownloadHistoryEvent.DHE_HISTORY_REMOVED, UPDATE_TYPE_DEAD );
										}

										historyUpdated( new_dh, DownloadHistoryEvent.DHE_HISTORY_ADDED, UPDATE_TYPE_ACTIVE );
									}
								}

								@Override
								public void
								downloadManagerRemoved(
									DownloadManager	dm )
								{
									synchronized( lock ){

										if ( !( enabled && isMonitored(dm))){

											return;
										}

										long uid = getUID( dm );

										DownloadHistoryImpl dh = getActiveHistory().remove( uid );

										if ( dh != null ){

											Map<Long,DownloadHistoryImpl> dead_history = getDeadHistory();

											dead_history.put( uid, dh );

											dh.setHistoryReference( dead_history );

											List<Tag> tags = (List<Tag>)dm.getUserData( TAG_CACHE_KEY );
											
											tags = TagUtils.sortTags( tags );
											
											dh.setRemoved( tags );

											historyUpdated( dh, DownloadHistoryEvent.DHE_HISTORY_MODIFIED, UPDATE_TYPE_BOTH );
										}
									}
								}
							}, false );

						global_manager.addDownloadWillBeRemovedListener((dm,b1,b2)->{
							
							try{
								List<Tag>	tags = tag_manager.getTagsForTaggable( TagType.TT_DOWNLOAD_MANUAL, dm );

								dm.setUserData( TAG_CACHE_KEY, tags );
							
							}catch( Throwable e ){
								
								Debug.out( e );;
							}
							
						});
						
						DownloadManagerFactory.addGlobalDownloadListener(
							new DownloadManagerAdapter()
							{
								@Override
								public void
								completionChanged(
									DownloadManager 	dm,
									boolean 			comp )
								{
									synchronized( lock ){

										if ( !( enabled && isMonitored(dm))){

											return;
										}

										long uid = getUID( dm );

										DownloadHistoryImpl dh = getActiveHistory().get( uid );

										if ( dh != null ){

											if ( dh.updateCompleteTime( dm.getDownloadState())){

												historyUpdated( dh, DownloadHistoryEvent.DHE_HISTORY_MODIFIED, UPDATE_TYPE_ACTIVE );
											}
										}
									}
								}
							});

						DownloadManagerStateFactory.addGlobalListener(
							new DownloadManagerStateAttributeListener() {

								@Override
								public void
								attributeEventOccurred(
									DownloadManager 	dm,
									String 				attribute,
									int 				event_type )
								{
									synchronized( lock ){

										if ( !( enabled && isMonitored(dm))){

											return;
										}

										long uid = getUID( dm );

										DownloadHistoryImpl dh = getActiveHistory().get( uid );

										if ( dh != null ){

											if ( dh.updateSaveLocation( dm )){

												historyUpdated( dh, DownloadHistoryEvent.DHE_HISTORY_MODIFIED, UPDATE_TYPE_ACTIVE );
											}
										}
									}
								}
							}, DownloadManagerState.AT_CANONICAL_SD_DMAP, DownloadManagerStateAttributeListener.WRITTEN );

						DownloadManagerStateFactory.addGlobalListener(
								new DownloadManagerStateAttributeListener() {

									@Override
									public void
									attributeEventOccurred(
										DownloadManager 	dm,
										String 				attribute,
										int 				event_type )
									{
										synchronized( lock ){

											if ( !( enabled && isMonitored(dm))){

												return;
											}

											long uid = getUID( dm );

											DownloadHistoryImpl dh = getActiveHistory().get( uid );

											if ( dh != null ){

												if ( dh.updateName( dm )){

													historyUpdated( dh, DownloadHistoryEvent.DHE_HISTORY_MODIFIED, UPDATE_TYPE_ACTIVE );
												}
											}
										}
									}
								}, DownloadManagerState.AT_DISPLAY_NAME, DownloadManagerStateAttributeListener.WRITTEN );

						if ( enabled ){

							if ( !FileUtil.resilientConfigFileExists( CONFIG_ACTIVE_FILE )){

								resetHistory();
							}
						}
					}
				}

				@Override
				public void
				stopping(
					Core core )
				{
					synchronized( lock ){

						writeHistory();

						COConfigurationManager.setParameter( CONFIG_ACTIVE_SIZE , active_history_size );
						COConfigurationManager.setParameter( CONFIG_DEAD_SIZE , dead_history_size );
					}
				}
			});

		if ( enabled ){
			try{
				PluginInterface default_pi = PluginInitializer.getDefaultInterface();
	
				default_pi.getUtilities().registerSearchProvider(
					new SearchProvider()
					{
						private Map<Integer,Object>	properties = new HashMap<>();
	
						{
							properties.put( PR_NAME, MessageText.getString( "downloadhistoryview.view.heading" ));
	
							try{
								URL url =
									MagnetURIHandler.getSingleton().registerResource(
										new MagnetURIHandler.ResourceProvider()
										{
											@Override
											public String
											getUID()
											{
												return( DownloadHistoryManager.class.getName() + ".1" );
											}
	
											@Override
											public String
											getFileType()
											{
												return( "png" );
											}
	
											@Override
											public byte[]
											getData()
											{
												try{
	
													InputStream is = getClass().getClassLoader().getResourceAsStream("com/biglybt/ui/images/sb/ic_logview.png");
	
													if ( is != null ){
	
														return( FileUtil.readInputStreamAsByteArray(is));
													}
												}catch( Throwable e ){
													
													Debug.out( e );
												}
												
												return( null );
											}
										});
	
								properties.put( PR_ICON_URL, url.toExternalForm());
	
							}catch( Throwable e ){
	
								Debug.out( e );
							}
						}
	
						@Override
						public SearchInstance
						search(
							Map<String,Object>	search_parameters,
							SearchObserver		observer )
	
							throws SearchException
						{
							try{
								return( searchHistory( search_parameters, observer ));
	
							}catch( Throwable e ){
	
								throw( new SearchException( "Search failed", e ));
							}
						}
	
						@Override
						public Object
						getProperty(
							int			property )
						{
							return( properties.get( property ));
						}
	
						@Override
						public void
						setProperty(
							int			property,
							Object		value )
						{
							properties.put( property, value );
						}
					});
	
			}catch( Throwable e ){
	
				Debug.out( "Failed to register search provider" );
			}
		}
		
		SimpleTimer.addPeriodicEvent(
			"DHM:timer",
			60*1000,
			new TimerEventPerformer() {

				@Override
				public void perform(TimerEvent event){

					synchronized( lock ){

						checkDiscard();
					}
				}
			});
	}

	@Override
	public boolean
	isEnabled()
	{
		return( enabled );
	}

	@Override
	public void
	setEnabled(
		boolean		enabled )
	{
		COConfigurationManager.setParameter( CONFIG_ENABLED, enabled );
	}

	void
	setEnabledSupport(
		boolean		b,
		boolean		startup )
	{
		synchronized( lock ){

			if ( enabled == b ){

				return;
			}

			enabled	= b;

			if ( !startup ){

				if ( enabled ){

					resetHistory();

				}else{

					clearHistory();
				}
			}
		}
	}

	boolean
	isMonitored(
		DownloadManager	dm )
	{
		if ( dm.isPersistent()){

			long	flags = dm.getDownloadState().getFlags();

			if (( flags & ( DownloadManagerState.FLAG_LOW_NOISE | DownloadManagerState.FLAG_METADATA_DOWNLOAD )) != 0 ){

				return( false );

			}else{

				return( true );
			}
		}

		return( false );
	}

	private void
	syncFromExisting(
		GlobalManager	global_manager )
	{
		if ( global_manager == null ){

			return;
		}

		synchronized( lock ){

			List<DownloadManager> dms = global_manager.getDownloadManagers();

			Map<Long,DownloadHistoryImpl>	history = getActiveHistory();

			if ( history.size() > 0 ){

				List<DownloadHistory> existing = new ArrayList<DownloadHistory>( history.values());

				history.clear();

				historyUpdated(new ArrayList<>(existing), DownloadHistoryEvent.DHE_HISTORY_REMOVED, UPDATE_TYPE_ACTIVE );
			}

			for ( DownloadManager dm: dms ){

				if ( isMonitored( dm )){

					DownloadHistoryImpl new_dh = new  DownloadHistoryImpl( history, dm );

					history.put( new_dh.getUID(), new_dh );
				}
			}

			historyUpdated( new ArrayList<DownloadHistory>( history.values()), DownloadHistoryEvent.DHE_HISTORY_ADDED, UPDATE_TYPE_ACTIVE );
		}
	}

	private List<DownloadHistory>
	getHistory()
	{
		synchronized( lock ){

			Map<Long,DownloadHistoryImpl>	active 	= getActiveHistory();
			Map<Long,DownloadHistoryImpl>	dead	= getDeadHistory();

			List<DownloadHistory>	result = new ArrayList<>(active.size() + dead.size());

			result.addAll( active.values());
			result.addAll( dead.values());

			return( result );
		}
	}

	@Override
	public int
	getHistoryCount()
	{
		return( active_history_size + dead_history_size );
	}

	@Override
	public void
	removeHistory(
		List<DownloadHistory>	to_remove )
	{
		synchronized( lock ){

			List<DownloadHistory> removed = new ArrayList<>(to_remove.size());

			int	update_type = 0;

			Map<Long,DownloadHistoryImpl>	active 	= getActiveHistory();
			Map<Long,DownloadHistoryImpl>	dead	= getDeadHistory();

			for ( DownloadHistory h: to_remove ){

				long	uid = h.getUID();

				DownloadHistoryImpl r = active.remove( uid );

				if ( r != null ){

					removed.add( r );

					update_type |= UPDATE_TYPE_ACTIVE;

				}else{

					r = dead.remove( uid );

					if ( r != null ){

						removed.add( r );

						update_type |= UPDATE_TYPE_DEAD;
					}
				}
			}

			if ( removed.size() > 0 ){

				historyUpdated( removed, DownloadHistoryEvent.DHE_HISTORY_REMOVED, update_type );
			}
		}
	}

	private void
	clearHistory()
	{
		synchronized( lock ){

			Map<Long,DownloadHistoryImpl>	active 	= getActiveHistory();
			Map<Long,DownloadHistoryImpl>	dead	= getDeadHistory();

			int	update_type = 0;

			List<DownloadHistory>	entries = getHistory();

			if ( active.size() > 0 ){

				active.clear();

				update_type |= UPDATE_TYPE_ACTIVE;
			}

			if ( dead.size() > 0 ){

				dead.clear();

				update_type |= UPDATE_TYPE_DEAD;
			}

			if ( update_type != 0 ){

				historyUpdated( entries, DownloadHistoryEvent.DHE_HISTORY_REMOVED, update_type );
			}
		}
	}

	@Override
	public void
	resetHistory()
	{
		synchronized( lock ){

			clearHistory();

			syncFromExisting( core.getGlobalManager());
		}
	}

	@Override
	public long[]
	getDates(
		byte[]		hash,
		boolean		with_redownload )
	{
		dates_cache_last_access		= SystemTime.getMonotonousTime();
		
		ByteArrayHashMap<long[]> cache = getDatesCache();
		
		long[] entry = cache.get(hash);

		if ( entry != null ){
			
			long uid = entry[0];
			
			Long rdl;
			
			if ( with_redownload ){
				
				rdl = redownload_cache.remove( uid) ;
				
			}else{
				
				rdl = redownload_cache.get( uid );
			}

			long[] result = { entry[1], entry[2], entry[3], rdl==null?0:rdl };

			return( result );
		}

		return( null );
	}

	private ByteArrayHashMap<long[]>
	getDatesCache()
	{
		ByteArrayHashMap<long[]> cache = dates_cache;
	
		int mut = mutation_count.get();
		
		if ( cache != null && mut == dates_cache_mutation_count ){
			
			return( cache );
		}
		
		cache = new ByteArrayHashMap<>();
		
		List<DownloadHistory> history = getHistory();

		for ( DownloadHistory dh: history ){
			
			long[] entry = { dh.getUID(), dh.getAddTime(), dh.getCompleteTime(), dh.getRemoveTime() };

			byte[] hash = dh.getTorrentHash();
			
			if ( hash != null ){
				
				cache.put( hash, entry );
			}
			
			hash = dh.getTorrentV2Hash();
			
			if ( hash != null ){
				
				cache.put( hash, entry );
			}
		}
		
		dates_cache 				= cache;
		dates_cache_mutation_count	= mut;
		dates_cache_last_access		= SystemTime.getMonotonousTime();
		
		return( cache );
	}
	
	void
	setRedownloading(
		DownloadHistory		dh )
	{
		redownload_cache.put( dh.getUID(), SystemTime.getCurrentTime());
	}

	static long
	getUID(
		DownloadManager		dm )
	{
		TOTorrent torrent = dm.getTorrent();

		long	lhs;

		if ( torrent == null ){

			lhs = 0;

		}else{

			try{
				byte[] hash = torrent.getHash();

				lhs = (hash[0]<<24)&0xff000000 | (hash[1] << 16)&0x00ff0000 | (hash[2] << 8)&0x0000ff00 | hash[3]&0x000000ff;

			}catch( Throwable e ){

				lhs = 0;
			}
		}


		long date_added = dm.getDownloadState().getLongAttribute( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );

		long	rhs = date_added/1000;

		return( ( lhs << 32 ) | rhs );
	}

	@Override
	public void
	addListener(
		DownloadHistoryListener		listener,
		boolean						fire_for_existing )
	{
		synchronized( lock ){

			history_escaped = true;

			listeners.addListener( listener );

			if ( fire_for_existing ){

				List<DownloadHistory> history = getHistory();

				listeners.dispatch( listener, 0, new DownloadHistoryEventImpl( DownloadHistoryEvent.DHE_HISTORY_ADDED, history ));
			}
		}
	}

	@Override
	public void
	removeListener(
		DownloadHistoryListener		listener )
	{
		synchronized( lock ){

			listeners.removeListener( listener );

			if ( listeners.size() == 0 ){

				history_escaped = false;
			}
		}
	}

	Map<Long,DownloadHistoryImpl>
	getActiveHistory()
	{
		Map<Long,DownloadHistoryImpl>	ref = history_active.get();

		if ( ref == null ){

			ref = loadHistory( CONFIG_ACTIVE_FILE );

			active_load_time = SystemTime.getMonotonousTime();

			history_active = new WeakReference<>(ref);

			active_history_size = ref.size();
		}

		return( ref );
	}

	Map<Long,DownloadHistoryImpl>
	getDeadHistory()
	{
		Map<Long,DownloadHistoryImpl>	ref = history_dead.get();

		if ( ref == null ){

			ref = loadHistory( CONFIG_DEAD_FILE );

			dead_load_time = SystemTime.getMonotonousTime();

			history_dead = new WeakReference<>(ref);

			dead_history_size = ref.size();
		}

		return( ref );
	}

	void
	historyUpdated(
		DownloadHistory			dh,
		int						action,
		int						type )
	{
		List<DownloadHistory> list = new ArrayList<>(1);

		list.add( dh );

		historyUpdated( list, action, type );
	}

	private void
	historyUpdated(
		Collection<DownloadHistory>		list,
		int								action,
		int								type )
	{
		mutation_count.incrementAndGet();
		
		if (( type & UPDATE_TYPE_ACTIVE ) != 0 ){

			Map<Long,DownloadHistoryImpl>	active = getActiveHistory();

			active_history_size = active.size();

			active_dirty = active;
		}

		if (( type & UPDATE_TYPE_DEAD ) != 0 ){

			Map<Long,DownloadHistoryImpl>	dead = getDeadHistory();

			dead_history_size = dead.size();

			dead_dirty	= dead;
		}

		if ( write_pending_event == null ){

			write_pending_event =
				SimpleTimer.addEvent(
					"DHL:write",
					SystemTime.getOffsetTime( 15*1000 ),
					new TimerEventPerformer() {

						@Override
						public void perform(TimerEvent event) {

							synchronized( lock ){

								write_pending_event = null;

								writeHistory();
							}
						}
					});
		}

		listeners.dispatch( 0, new DownloadHistoryEventImpl( action, new ArrayList<>(list)));
	}

	void
	checkDiscard()
	{
		long	now = SystemTime.getMonotonousTime();

		if ( now - dates_cache_last_access > 60*1000 ){
			
			dates_cache	= null;
		}
		
		if ( history_escaped ){

			return;
		}

		if ( now - active_load_time > 30*1000 && active_dirty == null && history_active.get() != null ){

			history_active.clear();
		}

		if ( now - dead_load_time > 30*1000 && dead_dirty == null && history_dead.get() != null ){

			history_dead.clear();
		}
	}

	void
	writeHistory()
	{
		if ( active_dirty != null ){

			saveHistory( CONFIG_ACTIVE_FILE, active_dirty );

			active_dirty = null;
		}

		if ( dead_dirty != null ){

			saveHistory( CONFIG_DEAD_FILE, dead_dirty );

			dead_dirty = null;
		}
	}

	private Map<Long,DownloadHistoryImpl>
	loadHistory(
		String		file )
	{
		Map<Long,DownloadHistoryImpl>	result = new HashMap<>();

		// System.out.println( "loading " + file );

		try{
			if ( FileUtil.resilientConfigFileExists( file )){

				Map map = FileUtil.readResilientConfigFile( file );

				List<Map<String,Object>>	list = (List<Map<String,Object>>)map.get( "records" );

				for ( Map<String,Object> m: list ){

					try{
						DownloadHistoryImpl record = new DownloadHistoryImpl( result, m );

						result.put( record.getUID(), record );

					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}
		}catch( Throwable e ){

			Debug.out( e );
		}

		return( result );
	}

	private void
	saveHistory(
		String 							file,
		Map<Long,DownloadHistoryImpl>	records )
	{
		// System.out.println( "saving " + file );

		try{
			Map<String,Object>	map = new HashMap<>();

			List<Map<String,Object>>	list = new ArrayList<>(records.size());

			map.put( "records", list );

			for ( DownloadHistoryImpl record: records.values()){

				try{
					Map<String,Object>	m = record.exportToMap();

					list.add( m );

				}catch( Throwable e ){

					Debug.out( e );
				}
			}

			FileUtil.writeResilientConfigFile( file, map );

		}catch( Throwable e ){

			Debug.out( e );
		}
	}

	
	public SearchInstance
	searchHistory(
		Map<String,Object>		search_parameters,
		final SearchObserver	observer )

		throws SearchException
	{
		final String	term = (String)search_parameters.get( SearchProvider.SP_SEARCH_TERM );

		final SearchInstance si =
			new SearchInstance()
			{
				@Override
				public void
				cancel()
				{
					Debug.out( "Cancelled" );
				}
			};

		if ( term == null ){

			try{
				observer.complete();

			}catch( Throwable e ){

				Debug.out( e );
			}
		}else{

			new AEThread2( "DownloadHistory:search", true )
			{
				@Override
				public void
				run()
				{
					try{
						List<DownloadHistory>	history = getHistory();
						
						SearchMatcher	matcher = new SearchMatcher( term );
	
						ByteArrayHashMap<String>	hashes = new ByteArrayHashMap<>();

						for ( DownloadHistory entry: history ){
							
							if ( matcher.matches( entry.getName())){
								
								byte[] hash = entry.getTorrentHash();
								
								if ( hash != null ){
									
									if ( hashes.containsKey(hash)){
										
										continue;
									}
									
									hashes.put( hash, "" );
								}
								
								Map result_properties = entry.toPropertyMap();
	
								SearchResult search_result =
									new SearchResult()
									{
										@Override
										public Object
										getProperty(
											int		property_name )
										{
											return( result_properties.get( property_name ));
										}
									};
	
								try{
									observer.resultReceived( si, search_result );
	
								}catch( Throwable e ){
	
									Debug.out( e );
								}
							}
						}

					}catch( Throwable e ){

						Debug.out( e );

					}finally{

						observer.complete();
					}
				}
			}.start();
		}

		return( si );
	}
	
	private static class
	DownloadHistoryEventImpl
		implements DownloadHistoryEvent
	{
		private final int						type;
		private final List<DownloadHistory>	history;

		DownloadHistoryEventImpl(
			int						_type,
			List<DownloadHistory>	_history )
		{
			type	= _type;
			history	= _history;
		}

		@Override
		public int
		getEventType()
		{
			return( type );
		}

		@Override
		public List<DownloadHistory>
		getHistory()
		{
			return( history );
		}
	}

	private static String[] NO_TAGS = {};
	
	private class
	DownloadHistoryImpl
		implements DownloadHistory
	{
		private final long 		uid;
		private final byte[]	hash;
		private final byte[]	hash_v2;
		private final long		size;
		private final long		add_time;
		
		private String			name;
		private String			save_location;
		private String[]		tags			= NO_TAGS;
		private long			complete_time	= -1;
		private long			remove_time		= -1;

		private Map<Long,DownloadHistoryImpl>	history_ref;	// need this for GC purposes

		DownloadHistoryImpl(
			Map<Long,DownloadHistoryImpl>		_history_ref,
			DownloadManager						dm )
		{
			history_ref	= _history_ref;

			uid		= DownloadHistoryManagerImpl.getUID( dm );

			byte[]	h1 	= null;
			byte[]	h2 	= null;
			
			TOTorrent torrent = dm.getTorrent();

			if ( torrent != null ){

				try{
					h1 	= torrent.getFullHash( TOTorrent.TT_V1 );

					if ( h1 == null ){
						
						h1 = torrent.getHash();
					}
					
					h2 	= torrent.getFullHash( TOTorrent.TT_V2 );
					
				}catch( Throwable e ){
				}
			}

			hash	= h1;
			hash_v2	= h2;
			
			name	= dm.getDisplayName();

			size	= dm.getSize();

			save_location	= dm.getSaveLocation().getAbsolutePath();

			DownloadManagerState	dms = dm.getDownloadState();

			add_time 		= dms.getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );

				// tags only populated on removal
			
			updateCompleteTime( dms );
		}

		DownloadHistoryImpl(
			Map<Long,DownloadHistoryImpl>		_history_ref,
			Map<String,Object>					map )

			throws IOException
		{
			history_ref	= _history_ref;

			try{
				uid		= (Long)map.get( "u" );
				hash	= (byte[])map.get("h");
				hash_v2	= (byte[])map.get( "v" );
				
				name 			= new String((byte[])map.get( "n"), Constants.UTF_8 );
				save_location 	= new String((byte[])map.get( "s"), Constants.UTF_8 );

				Long l_size		= (Long)map.get( "z" );

				size = l_size==null?0:l_size;

				add_time 		= (Long)map.get( "a" );
				complete_time 	= (Long)map.get( "c" );
				remove_time 	= (Long)map.get( "r" );

				List<byte[]> l_tags = (List<byte[]>)map.get( "t" );
				
				if ( l_tags != null ){
					
					tags = new String[l_tags.size()];
					
					for ( int i=0;i<l_tags.size();i++){
						
						tags[i] = new String( l_tags.get(i), Constants.UTF_8 );
					}
				}
			}catch( Throwable e ){

				throw( new IOException( "History decode failed: " + Debug.getNestedExceptionMessage( e )));
			}
		}

		void
		setHistoryReference(
			Map<Long,DownloadHistoryImpl>		ref )
		{
			history_ref	= ref;
		}

		Map<String,Object>
		exportToMap()

			throws IOException
		{
			Map<String,Object> map = new LightHashMap<>();

			map.put( "u", uid );
			map.put( "h", hash );
			
			if ( hash_v2 != null ){
				map.put( "v", hash_v2 );
			}
			
			map.put( "n", name.getBytes( Constants.UTF_8 ));
			map.put( "z", size );
			map.put( "s", save_location.getBytes( Constants.UTF_8 ));
			map.put( "a", add_time );
			map.put( "c", complete_time );
			map.put( "r", remove_time );

			if ( tags != null && tags.length > 0 ){
				
				List<byte[]>	l_tags = new ArrayList<>();
				
				for ( String tag: tags ){
					
					l_tags.add( tag.getBytes( Constants.UTF_8 ));
				}
				
				map.put( "t", l_tags );
			}
			
			return( map );
		}

		public Map<Integer,Object>
		toPropertyMap()
		{
			Map<Integer,Object>	result = new HashMap<>();
			
			result.put( SearchResult.PR_NAME, name );
			result.put( SearchResult.PR_SIZE, size );
			result.put( SearchResult.PR_HASH, hash );
			result.put( SearchResult.PR_PUB_DATE, new Date( add_time ));
						
			if ( hash != null ){
				
				String magnet = UrlUtils.getMagnetURI( hash, getTorrentV2Hash(), getName(), null );
				
				if ( tags != null && tags.length > 0 ){
				
					result.put( SearchResult.PR_TAGS, tags );
					
					for ( String tag: tags ){
						
						magnet += "&tag=" + UrlUtils.encode( tag );
					}
				}
	
				result.put( SearchResult.PR_TORRENT_LINK, magnet );
				//result.put( SearchResult.PR_DOWNLOAD_LINK, magnet );
			}

			return( result );
		}
		
		boolean
		updateCompleteTime(
			DownloadManagerState		dms )
		{
			long	old_time = complete_time;

			long comp = dms.getLongAttribute( DownloadManagerState.AT_COMPLETE_LAST_TIME );

			if ( comp == 0 ){

				complete_time = dms.getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME );	// nothing recorded either way

			}else{

				complete_time = comp;
			}

			return( complete_time != old_time );
		}

		boolean
		updateSaveLocation(
			DownloadManager		dm )
		{
			String old_location = save_location;

			String loc = dm.getSaveLocation().getAbsolutePath();

			if ( !loc.equals( old_location )){

				save_location = loc;

				return( true );

			}else{

				return( false );
			}
		}
		
		boolean
		updateName(
			DownloadManager		dm )
		{
			String name2 = dm.getDisplayName();
			
			if ( !name.equals( name2 )){

				name = name2;

				return( true );

			}else{

				return( false );
			}
		}

		@Override
		public long
		getUID()
		{
			return( uid );
		}

		@Override
		public byte[]
		getTorrentHash()
		{
			return( hash );
		}
		
		@Override
		public byte[]
		getTorrentV2Hash()
		{
			return( hash_v2 );
		}
		

		@Override
		public String
		getName()
		{
			return( name );
		}

		@Override
		public long
		getSize()
		{
			return( size );
		}

		@Override
		public String
		getSaveLocation()
		{
			return( save_location );
		}

		@Override
		public long
		getAddTime()
		{
			return( add_time );
		}

		@Override
		public long
		getCompleteTime()
		{
			return( complete_time );
		}

		@Override
		public String[] 
		getTags()
		{
			return( tags );
		}
		
		void
		setRemoved(
			List<Tag>	removal_tags  )
		{
			remove_time	= SystemTime.getCurrentTime();
			
			if ( removal_tags != null && !removal_tags.isEmpty()){
				
				tags = new String[removal_tags.size()];
					
				for ( int i=0; i<removal_tags.size(); i++ ){
						
					tags[i] = removal_tags.get(i).getTagName( true );
				}
			}
		}

		@Override
		public long
		getRemoveTime()
		{
			return( remove_time );
		}

		@Override
		public void
		setRedownloading()
		{
			DownloadHistoryManagerImpl.this.setRedownloading( this );
		}
	}
}
