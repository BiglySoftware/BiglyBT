/*
 * Created on Sep 4, 2013
 * Created by Paul Gardner
 *
 * Copyright 2013 Azureus Software, Inc.  All rights reserved.
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
 */


package com.biglybt.core.tag.impl;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.CoreLifecycleAdapter;
import com.biglybt.core.CoreRunningListener;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ConfigUtils;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerListener;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.DownloadManagerStateAttributeListener;
import com.biglybt.core.download.DownloadManagerStats;
import com.biglybt.core.download.impl.DownloadManagerAdapter;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.ipfilter.IpFilter;
import com.biglybt.core.ipfilter.IpFilterManagerFactory;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.core.tag.TagFeatureProperties.TagPropertyListener;
import com.biglybt.core.torrent.PlatformTorrentUtils;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentAnnounceURLGroup;
import com.biglybt.core.torrent.TOTorrentAnnounceURLSet;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.client.TRTrackerScraperResponse;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginAdapter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadListener;
import com.biglybt.pif.download.DownloadScrapeResult;
import com.biglybt.pif.sharing.ShareManager;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pif.torrent.TorrentManager;
import com.biglybt.pifimpl.local.PluginCoreUtils;

public class
TagPropertyConstraintHandler
	implements TagTypeListener, DownloadListener
{
	private static final Object DM_LISTENERS_ADDED				= new Object();
	
	private static final Object DM_FILE_NAMES 					= new Object();		// torrent names, never change
	private static final Object DM_FILE_NAMES_SELECTED 			= new Object();
	private static final Object DM_FILE_EXTS					= new Object();		// torrent exts, never change
	private static final Object DM_FILE_EXTS_SELECTED			= new Object();
	private static final Object DM_FILE_PATHS					= new Object();
	private static final Object DM_FILE_PATHS_SELECTED			= new Object();
	
	private static final Object DM_NAME							= new Object();
	private static final Object DM_SAVE_PATH					= new Object();

	private static final Object DM_PEER_SETS					= new Object();
	private static final Object DM_RATES						= new Object();
	
	private static final Object DM_TRACKERS						= new Object();
	
	private static final String		EVAL_CTX_COLOURS 	= "colours";
	private static final String		EVAL_CTX_TAG_SORT 	= "tag_sort";
	
	private final Core core;
	private final TagManagerImpl	tag_manager;
	private final ShareManager		share_manager;
	
	
	private volatile boolean		initialised;
	
	private boolean 	initial_assignment_complete;
	private boolean		stopping;

	private String	ta_rating_name;

	final Map<Tag,TagConstraint>	constrained_tags 	= new ConcurrentHashMap<>();

	private boolean	dm_listener_added;

	final Map<Tag,Map<DownloadManager,Long>>			apply_history 		= new HashMap<>();

	private final AsyncDispatcher	dispatcher = new AsyncDispatcher( "tag:constraints" );

	private final FrequencyLimitedDispatcher	freq_lim_dispatcher =
		new FrequencyLimitedDispatcher(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					checkFreqLimUpdates();
				}
			},
			5000 );

	final IdentityHashMap<DownloadManager,Set<TagConstraint>>	freq_lim_pending = new IdentityHashMap<>();

	private DownloadManagerListener dm_listener = 
		new DownloadManagerAdapter(){
			
			private Object[] keys = {
				DM_FILE_NAMES_SELECTED,
				DM_FILE_EXTS_SELECTED,
				DM_FILE_PATHS_SELECTED };
		
			@Override
			public void 
			filePriorityChanged(
				DownloadManager 	download, 
				DiskManagerFileInfo file )
			{
				boolean changed = false;
				
				for ( Object key: keys ){
					if ( download.getUserData( key ) != null ){
					
						changed = true;
					}
					download.setUserData( key, null );
				}
				
				if ( changed ){
					nameEtcChanged( download );
				}
			}
		};
		
	private DownloadManagerStateAttributeListener dms_listener = 
		new DownloadManagerStateAttributeListener()
		{
			private Object[] keys = {
				DM_NAME,
				DM_SAVE_PATH,
				DM_FILE_PATHS,
				DM_FILE_PATHS_SELECTED };

			public void 
			attributeEventOccurred(
				DownloadManager download, 
				String 			attribute, 
				int 			event_type)
			{
				boolean changed = false;
				
				for ( Object key: keys ){
					if ( download.getUserData( key ) != null ){
					
						changed = true;
					}
					download.setUserData( key, null );
				}
				
				if ( changed ){
					nameEtcChanged( download );
				}
			};	
		};
	
	private static IpFilter	ip_filter = IpFilterManagerFactory.getSingleton().getIPFilter();
		
	private static volatile int	apply_all_secs;
	private static volatile int	target_share_ratio;
	
	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				ConfigKeys.Tag.ICFG_TAG_AUTO_FULL_REAPPLY_PERIOD_SECS,
				"Stop Ratio",
			},
			(n)->{
				apply_all_secs = COConfigurationManager.getIntParameter(ConfigKeys.Tag.ICFG_TAG_AUTO_FULL_REAPPLY_PERIOD_SECS);
				
				if ( apply_all_secs <= 0 ){
					apply_all_secs = 0;
				}else if ( apply_all_secs < 10 ){
					apply_all_secs = 10;
				}
				
				target_share_ratio = (int)( 1000*COConfigurationManager.getFloatParameter( "Stop Ratio" ) );
			});
	}
	
	private TimerEventPeriodic		timer;

	protected
	TagPropertyConstraintHandler(
		Core 			_core,
		TagManagerImpl	_tm )
	{
		core			= _core;
		tag_manager		= _tm;

		ShareManager sm;
		
		PluginInterface default_pi = core.getPluginManager().getDefaultPluginInterface();
		
		try{
			sm	= default_pi.getShareManager();
			
		}catch(  Throwable e ){
			
			Debug.out( e );
			
			sm = null;
		}
		
		share_manager = sm;
		
		try{
			default_pi.addListener(
				new PluginAdapter()
				{
					@Override
					public void 
					initializationComplete()
					{
						default_pi.removeListener( this );
						
						PluginInterface rating_pi = core.getPluginManager().getPluginInterfaceByID( "azrating" );
						
						if ( rating_pi != null ){
							
							TorrentManager tm = rating_pi.getTorrentManager();
							
						    TorrentAttribute ta_rating = tm.getPluginAttribute("rating");
						    
						    if ( ta_rating != null ){
						    	
						    	ta_rating_name = ta_rating.getName();
						    }
						}
					}
				});
			

			
		}catch(  Throwable e ){
		}
				
		core.addLifecycleListener(
			new CoreLifecycleAdapter()
			{
				@Override
				public void
				stopping(Core core)
				{
					stopping	= true;
				}
			});
		
		
		tag_manager.addTaggableLifecycleListener(
			Taggable.TT_DOWNLOAD,
			new TaggableLifecycleAdapter()
			{
				@Override
				public void
				initialised(
					List<Taggable>	current_taggables )
				{
					try{
						TagType tt_manual_download = tag_manager.getTagType( TagType.TT_DOWNLOAD_MANUAL );

						tt_manual_download.addTagTypeListener( TagPropertyConstraintHandler.this, true );

					}finally{

						CoreFactory.addCoreRunningListener(
							new CoreRunningListener()
							{
								@Override
								public void
								coreRunning(
									Core core )
								{
									synchronized( constrained_tags ){

										initialised = true;

										checkRecompiles();
										
										apply( core.getGlobalManager().getDownloadManagers(), true );
									}
								}
							});
					}
				}

				@Override
				public void
				taggableCreated(
					Taggable		taggable )
				{
					DownloadManager dm = (DownloadManager)taggable;
					
					long added = dm.getDownloadState().getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );

						// sanity check
					
					boolean	is_new = SystemTime.getCurrentTime() - added < 5*60*1000;
					
					apply( dm, null, false, is_new );
				}
			});
		
		tag_manager.addTagManagerListener(
			new TagManagerListener(){
				
				@Override
				public void tagTypeRemoved(TagManager manager, TagType tag_type){
					// TODO Auto-generated method stub
					
				}
				
				@Override
				public void tagTypeAdded(TagManager manager, TagType tag_type){
					if ( tag_type.getTagType() == TagType.TT_PEER_IPSET ){
						tag_type.addTagTypeListener(
							new TagTypeListener()
							{
								@Override
								public void 
								tagEventOccurred(
									TagEvent event)
								{
									if ( event.getEventType() == TagEvent.ET_TAG_ADDED ){
										
										checkRecompiles();
									}
								}
								
								@Override
								public void 
								tagTypeChanged(
									TagType tag_type)
								{
								}
							}, false );
					}
				}}, true );
				
	}

	private void
	checkRecompiles()
	{
		for ( Tag tag: constrained_tags.keySet()){
			
			if ( tag.getTransientProperty( Tag.TP_CONSTRAINT_ERROR ) != null ){
	
				TagFeatureProperties tfp = (TagFeatureProperties)tag;

				TagProperty prop = tfp.getProperty( TagFeatureProperties.PR_CONSTRAINT );

				handleProperty( prop, true );
			}
		}
	}
	
	private static Object	process_lock = new Object();
	private static int		processing_disabled_count;

	private static List<Object[]>	processing_queue = new ArrayList<>();

	public void
	setProcessingEnabled(
		boolean	enabled )
	{
		synchronized( process_lock ){

			if ( enabled ){

				processing_disabled_count--;

				if ( processing_disabled_count == 0 ){

					List<Object[]> to_do = new ArrayList<>(processing_queue);

					processing_queue.clear();

					for ( Object[] entry: to_do ){

						TagConstraint 	constraint 	= (TagConstraint)entry[0];
						Object			target		= entry[1];
						boolean			is_new		= (Boolean)entry[2];
						
						try{

							if ( target instanceof DownloadManager ){

								constraint.apply((DownloadManager)target, is_new );

							}else{

								constraint.apply((List<DownloadManager>)target);
							}
						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			}else{

				processing_disabled_count++;
			}
		}
	}

	private static boolean
	canProcess(
		TagConstraint		constraint,
		DownloadManager		dm,
		boolean				is_new )
	{
		synchronized( process_lock ){

			if ( processing_disabled_count == 0 ){

				return( true );

			}else{

				processing_queue.add( new Object[]{ constraint, dm, is_new });

				return( false );
			}
		}
	}

	private static boolean
	canProcess(
		TagConstraint				constraint,
		List<DownloadManager>		dms )
	{
		synchronized( process_lock ){

			if ( processing_disabled_count == 0 ){

				return( true );

			}else{

				processing_queue.add( new Object[]{ constraint, dms, false });

				return( false );
			}
		}
	}

	private void
	checkDMListeners(
		DownloadManager		dm )
	{
		synchronized( DM_LISTENERS_ADDED ){
			
			if ( dm.getUserData( DM_LISTENERS_ADDED ) == null ){
				
				dm.addListener( dm_listener );
				
				DownloadManagerState dms = dm.getDownloadState();
				
				dms.addListener( 
					dms_listener, 
					DownloadManagerState.AT_FILE_LINKS2, 
					DownloadManagerStateAttributeListener.WRITTEN );
				
				dms.addListener( 
						dms_listener, 
						DownloadManagerState.AT_CANONICAL_SD_DMAP, 
						DownloadManagerStateAttributeListener.WRITTEN );
				
				dms.addListener( 
						dms_listener, 
						DownloadManagerState.AT_DISPLAY_NAME, 
						DownloadManagerStateAttributeListener.WRITTEN );

				dm.setUserData( DM_LISTENERS_ADDED, "" );
			}
		}
	}
	
	@Override
	public void
	tagTypeChanged(
		TagType		tag_type )
	{
	}

	@Override
	public void 
	tagEventOccurred(
		TagEvent event ) 
	{
		int	type = event.getEventType();
		
		Tag	tag = event.getTag();
		
		if ( type == TagEvent.ET_TAG_ADDED ){
			
			tagAdded( tag );
			
		}else if ( type == TagEvent.ET_TAG_REMOVED ){
			
			tagRemoved( tag );
			
		}else if ( type == TagEvent.ET_TAG_METADATA_CHANGED ){
			
			TagConstraint tc = constrained_tags.get( tag );
			
			if ( tc != null ){
				
				tc.checkStuff();
			}
		}
	}

	public void
	tagAdded(
		Tag			tag )
	{
		TagFeatureProperties tfp = (TagFeatureProperties)tag;

		TagProperty prop = tfp.getProperty( TagFeatureProperties.PR_CONSTRAINT );

		if ( prop != null ){

			prop.addListener(
				new TagPropertyListener()
				{
					@Override
					public void
					propertyChanged(
						TagProperty		property )
					{
						handleProperty( property, false );
					}

					@Override
					public void
					propertySync(
						TagProperty		property )
					{
					}
				});

			handleProperty( prop, false );
		}

		tag.addTagListener(
			new TagListener()
			{
				@Override
				public void
				taggableSync(
					Tag tag )
				{
				}

				@Override
				public void
				taggableRemoved(
					Tag 		tag,
					Taggable 	tagged )
				{
					apply((DownloadManager)tagged, tag, true, false );
				}

				@Override
				public void
				taggableAdded(
					Tag 		tag,
					Taggable 	tagged )
				{
					apply((DownloadManager)tagged, tag, true, false );
				}
			}, false );
	}

	private void
	checkTimer()
	{
			// already synchronized on constrainted_tags by callers

		if ( constrained_tags.size() > 0 ){

			if ( timer == null ){

				final int	TICK_SECS = 5;	// must divide into 60!
				
				final int	MIN_TICKS = 60 / TICK_SECS;
					
				timer =
					SimpleTimer.addPeriodicEvent(
						"tag:constraint:timer",
						TICK_SECS*1000,
						new TimerEventPerformer() {

							int tick_count = 0;
							
							boolean apply_done = false;
							
							@Override
							public void
							perform(
								TimerEvent event)
							{
								tick_count++;
								
								if ( tick_count % MIN_TICKS == 0 ){
									
									GlobalManager gm = core.getGlobalManager();

									List<DownloadManager> all_dms = gm.getDownloadManagers();
																		
									for ( DownloadManager dm: all_dms ){
										
										int state = dm.getState();
										
										if ( state == DownloadManager.STATE_DOWNLOADING || state == DownloadManager.STATE_SEEDING ){
											
											long[] rates = (long[])dm.getUserData( DM_RATES );
											
											if ( rates != null ){
												
												DownloadManagerStats stats = dm.getStats();
												
												long down	= stats.getTotalDataBytesReceived() + stats.getTotalProtocolBytesReceived();
												long up		= stats.getTotalDataBytesSent() + stats.getTotalProtocolBytesSent();
												
												if ( rates[0] != -1 ){
																										
													long down_diff	= down - rates[0];
													long up_diff	= up - rates[1];
													
													rates[2] = down_diff/60;
													rates[3] = up_diff/60;
												}
												
												rates[0] = down;
												rates[1] = up;
											}
										}
									}
								}
								
								Set<Tag>				peer_sets 	= new HashSet<>();
								List<TagConstraint>		ps_constraints;
								List<TagConstraint>		time_constraints;
								
								List<DownloadManager>	ps_changed	= Collections.emptyList();
								
								synchronized( constrained_tags ){
									
									ps_constraints 		= new ArrayList<>( constrained_tags.size());
									time_constraints 	= new ArrayList<>( constrained_tags.size());
									
									for ( TagConstraint tc: constrained_tags.values()){
											
										if ( tc.getDependsOnLevel() == TagConstraint.DEP_TIME ){
											
											time_constraints.add( tc );
										}
										
										Set<Tag> tags = tc.getDependsOnTags();
									
										boolean added = false;
										
										for ( Tag tag: tags ){
											
											if ( tag.getTagType().getTagType() == TagType.TT_PEER_IPSET ){
												
												peer_sets.add( tag );
												
												added = true;
											}
										}
										
										if ( added ){
											
											ps_constraints.add( tc );
										}
									}
								}
								
								if ( !peer_sets.isEmpty()){
									
									GlobalManager gm = core.getGlobalManager();
									
									Map<DownloadManager,Set<Tag>>	dm_map = new IdentityHashMap<>();
									
									for ( Tag ps: peer_sets ){
										
										Set<Taggable> peers = ps.getTagged();
										
										for ( Taggable peer: peers ){
											
											PEPeer pe_peer = (PEPeer)peer;
											
											byte[] dl_hash = pe_peer.getManager().getHash();
											
											DownloadManager dm = gm.getDownloadManager(  new HashWrapper( dl_hash ));
											
											if ( dm != null ){
												
												Set<Tag> s = dm_map.get( dm );
														
												if ( s == null ){
													
													s = new HashSet<>();
													
													dm_map.put( dm, s );
												}
												
												s.add( ps );
											}
										}
									}
									
									
									List<DownloadManager> all_dms = gm.getDownloadManagers();
									
									ps_changed = new ArrayList<>( all_dms.size());
									
									for ( DownloadManager dm: all_dms ){
										
										Set<Tag>	current	= dm_map.get( dm );
										
										Set<Tag>	existing = (Set<Tag>)dm.getUserData( DM_PEER_SETS );
										
										if ( current != existing ){
											
											if ( current == null ){
												
												dm.setUserData( DM_PEER_SETS, null );
												
												ps_changed.add( dm );
												
											}else if ( existing == null ){
												
												dm.setUserData( DM_PEER_SETS, current );
												
												ps_changed.add( dm );
												
											}else{
												
												if ( !existing.equals( current )){
													
													dm.setUserData( DM_PEER_SETS, current );
													
													ps_changed.add( dm );
												}
											}
										}
									}
								}
								
								if ( tick_count % 6 == 0 ){
								
									apply_history.clear();
								}
								
								boolean did_apply_all = false;
								
								if ( apply_all_secs == 0 ){
								
									if ( !apply_done ){
										
										did_apply_all = applyAll();											
									}
								}else{
									
									int apply_all_ticks = apply_all_secs/TICK_SECS;
								
									if ( tick_count % apply_all_ticks == 0 ){
								
										did_apply_all = applyAll();											
									}
								}
								
								if ( did_apply_all ){
									
									apply_done = true;
									
								}else{
									
									if ( !time_constraints.isEmpty()){
									
										GlobalManager gm = core.getGlobalManager();

										apply( gm.getDownloadManagers(), time_constraints );
									}
									
									if ( !ps_changed.isEmpty()){
										
										apply( ps_changed, ps_constraints );
									}
								}
							}
						});

				CoreFactory.addCoreRunningListener(
					new CoreRunningListener()
					{
						@Override
						public void
						coreRunning(
							Core core )
						{
							synchronized( constrained_tags ){

								if ( timer != null ){

									core.getPluginManager().getDefaultPluginInterface().getDownloadManager().getGlobalDownloadEventNotifier().addListener( TagPropertyConstraintHandler.this );

									dm_listener_added = true;
								}
							}
						}
					});
			}

		}else if ( timer != null ){

			timer.cancel();

			timer = null;

			if ( dm_listener_added ){

				core.getPluginManager().getDefaultPluginInterface().getDownloadManager().getGlobalDownloadEventNotifier().removeListener( this );
			}

			apply_history.clear();
		}
	}

	private void
	checkFreqLimUpdates()
	{
		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					synchronized( freq_lim_pending ){

						for ( Map.Entry<DownloadManager,Set<TagConstraint>> entry: freq_lim_pending.entrySet()){

							for ( TagConstraint con: entry.getValue()){

								con.apply( entry.getKey(), false );
							}
						}

						freq_lim_pending.clear();
					}
				}
			});
	}

	public void
	nameEtcChanged(
		DownloadManager		dm )
	{
		Set<TagConstraint>	interesting = new HashSet<>();
		
		synchronized( constrained_tags ){

			if ( !initialised ){

				return;
			}

			for ( TagConstraint tc: constrained_tags.values()){

				if ( tc.dependsOnNameEtc()){

					interesting.add( tc );
				}
			}
		}

		if ( interesting.size() > 0 ){

			synchronized( freq_lim_pending ){

				Set<TagConstraint> existing = freq_lim_pending.get( dm );
				
				if ( existing == null ){
				
					freq_lim_pending.put( dm, interesting );
					
				}else{
					
					existing.addAll( interesting );
				}
			}

			freq_lim_dispatcher.dispatch();
		}
	}
	
	@Override
	public void
	stateChanged(
		Download		download,
		int				old_state,
		int				new_state )
	{
		Set<TagConstraint>	interesting = new HashSet<>();

		synchronized( constrained_tags ){

			if ( !initialised ){

				return;
			}

			for ( TagConstraint tc: constrained_tags.values()){

				if ( tc.dependsOnDownloadState()){

					interesting.add( tc );
				}
			}
		}

		if ( interesting.size() > 0 ){

			DownloadManager dm = PluginCoreUtils.unwrap( download );

			synchronized( freq_lim_pending ){

				Set<TagConstraint> existing = freq_lim_pending.get( dm );
				
				if ( existing == null ){
				
					freq_lim_pending.put( dm, interesting );
					
				}else{
					
					existing.addAll( interesting );
				}
			}

			freq_lim_dispatcher.dispatch();
		}
	}

	@Override
	public void
	positionChanged(
		Download	download,
		int 		oldPosition,
		int 		newPosition )
	{
	}

	protected String
	getTagStatus(
		Tag	tag )
	{
		TagConstraint tc = constrained_tags.get( tag );
			
		if ( tc != null ){
			
			return( tc.getStatus());
		}
		
		return( null );
	}
	
	protected String[]
	explain(
		Tag				tag,
		Taggable		taggable )
	{
		TagConstraint tc = constrained_tags.get( tag );
		
		if ( tc == null ){
			
			return( new String[]{ "no constraint" });
			
		}else if ( ! ( taggable instanceof DownloadManager )){
			
			return( new String[]{ "invalid taggable" });
			
		}else{
			
			StringBuilder		debug = new StringBuilder( 1024 );
			
			boolean result = tc.testConstraint((DownloadManager)taggable, debug);
			
			return( new String[]{ String.valueOf( result ), tc.getString(), debug.toString()});
		}
	}
	
	protected Set<Tag>
	getDependsOnTags(
		Tag	tag )
	{
		TagConstraint tc = constrained_tags.get( tag );
		
		if ( tc != null ){
			
			return( tc.getDependsOnTags());
		}
		
		return( Collections.emptySet());
	}
	
	public void
	tagRemoved(
		Tag			tag )
	{
		synchronized( constrained_tags ){

			if ( constrained_tags.containsKey( tag )){

				constrained_tags.remove( tag );
				
				checkTimer();
			}
		}
	}

	private boolean
	isStopping()
	{
		return( stopping );
	}

	private void
	handleProperty(
		TagProperty		property,
		boolean			force )
	{
		Tag	tag = property.getTag();

		synchronized( constrained_tags ){

			boolean enabled = property.isEnabled();
			
			String[] value = property.getStringList();

			String 	constraint;
			String	options;

			if ( value == null ){

				constraint 	= "";
				options		= "";

			}else{

				constraint 	= value.length>0&&value[0]!=null?value[0].trim():"";
				options		= value.length>1&&value[1]!=null?value[1].trim():"";
			}

			if ( constraint.length() == 0 ){

				if ( constrained_tags.containsKey( tag )){

					constrained_tags.remove( tag );
					
					tag.setTransientProperty( Tag.TP_CONSTRAINT_ERROR, null );
				}
			}else{

				TagConstraint con = constrained_tags.get( tag );

				
				if ( !force ){
					
					if (	con != null && 
							con.getConstraint().equals( constraint ) && 
							con.getOptions().equals( options ) &&
							con.isEnabled() == enabled ){
	
						return;
					}
				}

				con = new TagConstraint( this, tag, constraint, options, enabled );

				constrained_tags.put( tag, con );

				if ( initialised ){

					apply( con );
				}
			}

			checkTimer();
		}
	}

	private void
	apply(
		final DownloadManager				dm,
		Tag									related_tag,
		boolean								auto,
		boolean								is_new )
	{
		if ( dm.isDestroyed()){

			return;
		}

		synchronized( constrained_tags ){

			if ( constrained_tags.size() == 0 || !initialised ){

				return;
			}

			if ( auto && !initial_assignment_complete ){

				return;
			}
		}

		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					List<TagConstraint>	cons;

					synchronized( constrained_tags ){

						cons = new ArrayList<>(constrained_tags.values());
					}

					for ( TagConstraint con: cons ){

						con.apply( dm, is_new );
					}
				}
			});
	}

	private void
	apply(
		final List<DownloadManager>		dms,
		final boolean					initial_assignment )
	{
		synchronized( constrained_tags ){

			if ( constrained_tags.size() == 0 || !initialised ){

				return;
			}
		}

		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					List<TagConstraint>	cons;

					synchronized( constrained_tags ){

						cons = new ArrayList<>(constrained_tags.values());
					}

						// set up initial constraint tagged state without following implications

					for ( TagConstraint con: cons ){

						con.apply( dms );
					}

					if ( initial_assignment ){

						synchronized( constrained_tags ){

							initial_assignment_complete = true;
						}

							// go over them one more time to pick up consequential constraints

						for ( TagConstraint con: cons ){

							con.apply( dms );
						}
					}
				}
			});
	}

	private void
	apply(
		final TagConstraint		constraint )
	{
		synchronized( constrained_tags ){

			if ( !initialised ){

				return;
			}
		}

		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					List<DownloadManager> dms = core.getGlobalManager().getDownloadManagers();

					constraint.apply( dms );
				}
			});
	}

	private void
	apply(
		List<DownloadManager> 	dms,
		List<TagConstraint>		cons )	
	{
		synchronized( constrained_tags ){

			if ( !initialised ){

				return;
			}
		}

		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					for ( TagConstraint con: cons ){

						con.apply( dms );
					}
				}
			});
	}
	
	private boolean
	applyAll()
	{
		synchronized( constrained_tags ){

			if ( constrained_tags.size() == 0 || !initialised ){

				return( false );
			}
		}

		dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					List<DownloadManager> dms = core.getGlobalManager().getDownloadManagers();

					List<TagConstraint>	cons;

					synchronized( constrained_tags ){

						cons = new ArrayList<>(constrained_tags.values());
					}

					for ( TagConstraint con: cons ){
						
						con.apply( dms );
					}
				}
			});
		
		return( true );
	}

	public TagConstraint
	compileConstraint(
		String		expr )
	{
		return( new TagConstraint( this, null, expr, null, true ));
	}

	private static Pattern comp_op_pattern	= Pattern.compile( "(.+?)(==|!=|>=|>|<=|<|\\+|-|\\*|/|%)(.+)");
	private static Pattern comp_ift_pattern	= Pattern.compile( "(.+?)\\?(.+):(.+)");
	
	private static Map<String,String>	comp_op_map = new HashMap<>();
	
	static{
		comp_op_map.put( "==", "isEQ" );
		comp_op_map.put( "!=", "isNEQ" );
		comp_op_map.put( ">=", "isGE" );
		comp_op_map.put( "<=", "isLE" );
		comp_op_map.put( ">",  "isGT" );
		comp_op_map.put( "<",  "isLT" );
		comp_op_map.put( "+",  "plus" );
		comp_op_map.put( "-",  "minus" );
		comp_op_map.put( "*",  "mult" );
		comp_op_map.put( "/",  "div" );
		comp_op_map.put( "%",  "rem" );
	}
	
	private static Map<String,Object[]>	config_value_cache = new ConcurrentHashMap<String, Object[]>();
	
	private static Map<String,String[]>	config_key_map = new HashMap<>();
	
	private static final String	CONFIG_FLOAT = "float";
	
	static{
		String[][] entries = {
				{ "queue.seeding.ignore.share.ratio", CONFIG_FLOAT, "Stop Ratio" },	
		};
		
		ParameterListener listener = 
			new ParameterListener()
			{
				@Override
				public void 
				parameterChanged(
					String parameterName)
				{	
					config_value_cache.clear();
				}
			};
			
		for ( String[] entry: entries ){
			
			config_key_map.put( entry[0], new String[]{ entry[1], entry[2] });
			
			COConfigurationManager.addParameterListener( entry[2], listener );	
		}
	}
	
	private static class
	TagConstraint
		implements com.biglybt.core.tag.TagConstraint
	{
		private final TagPropertyConstraintHandler	handler;
		private final Tag							tag_maybe_null;
		private final String						constraint;
		private final boolean						enabled;
		
		private final boolean		auto_add;
		private final boolean		auto_remove;
		private final boolean		new_only;		
		
		private final ConstraintExpr	expr;

		private boolean	depends_on_download_state;
		private boolean depends_on_names_etc;
		private int		depends_on_level			= DEP_STATIC;

		private Set<Tag>		dependent_on_tags;
		private boolean			dependent_on_peer_sets;
		
		private Set<Tag>		tag_weights;
		private int				tag_weights_opt	= 0;
		
		private Set<Tag>		tag_sorts;
		private int				tag_sorts_opt	= 2;	// def is cumulative
		
		private boolean			must_check_dependencies;
		
		private Average			activity_average = Average.getInstance( 1000, 60 );
		
		private String 			error;
		
		private
		TagConstraint(
			TagPropertyConstraintHandler	_handler,
			Tag								_tag,
			String							_constraint,
			String							options,
			boolean							_enabled )
		{
			handler			= _handler;
			tag_maybe_null	= _tag;
			
			constraint	= _constraint;
			enabled		= _enabled;
			
			if ( options == null ){

				auto_add	= true;
				auto_remove	= true;
				new_only	= false;
				
			}else if ( options.contains( "am=3;" )){
				
				auto_add	= false;
				auto_remove	= false;
				new_only	= true;
				
			}else{
					// 0 = add+remove; 1 = add only; 2 = remove only

				auto_add 	= !options.contains( "am=2;" );
				auto_remove = !options.contains( "am=1;" );
				new_only	= false;
			}
			
			checkStuff();			
			
			ConstraintExpr compiled_expr = null;

			setError( null );
			
			try{
				compiled_expr = compileStart( constraint, new HashMap<String,ConstraintExpr>());

				// System.out.println( "Compiled:\n" + constraint + " \n->\n" + compiled_expr.getString());
				
			}catch( Throwable e ){
				
				setError( "Invalid constraint: " + Debug.getNestedExceptionMessage( e ));

			}finally{

				expr = compiled_expr;
			}
		}

		private String
		getStatus()
		{
			String result = activity_average.getAverage() + "/" +  TimeFormatter.getLongSuffix( TimeFormatter.TS_SECOND );
			
			if ( Constants.IS_CVS_VERSION ){
				
				result +=  ", " + "DS=" + depends_on_download_state + ", DL=" + depends_on_level;
			}
			
			return( result );
		}
		
		private String
		getString()
		{
			return( expr==null?"Failed to compile constraint \"" + constraint + "\"":expr.getString());
		}
		
		private int
		getDependsOnLevel()
		{
			return( depends_on_level );
		}
		
		private Set<Tag>
		getDependsOnTags()
		{
			return( dependent_on_tags == null?Collections.emptySet():dependent_on_tags );
		}
		
		private void
		checkStuff()
		{
			// we're only bothered about assignments to tags that can have significant side-effects. Currently these are
			// 1) execute-on-assign tags
			// 2) tags with limits (and therefore removal policies such as 'delete download')
			// 3) new_downloads tags that only get evaluated once

			if ( new_only ){
				
				must_check_dependencies = true;
				
			}else{
				
				if ( tag_maybe_null != null ){
					
					if (((TagFeatureExecOnAssign)tag_maybe_null).isAnyActionEnabled()){
						
						must_check_dependencies = true;
						
					}else if ( (((TagFeatureLimits)tag_maybe_null).getMaximumTaggables() > 0 )){
						
						must_check_dependencies = true;
						
					}else{
						
						must_check_dependencies = false;
					}
				}
			}
		}
		
		private boolean
		isEnabled()
		{
			return( enabled );
		}
		
		private void
		setError(
			String		str )
		{
			error = null;
			
			if ( tag_maybe_null != null ){
				
				boolean already_error = false;
				
				if ( str != null && !str.isEmpty()){
					
					String existing = (String)tag_maybe_null.getTransientProperty( Tag.TP_CONSTRAINT_ERROR );
					
					if ( existing != null && !existing.isEmpty()){
						
						already_error = true;
					}
				}
				
				if ( !already_error ){
					
					tag_maybe_null.setTransientProperty( Tag.TP_CONSTRAINT_ERROR, str );
				
					if ( str != null && handler.initialised ){
				
						Debug.out( str );
					}
				}
			}
		}
		
		public String
		getError()
		{
			return( expr==null?"Failed to compile":error );
		}
		
		private boolean
		dependsOnDownloadState()
		{
			return( depends_on_download_state );
		}			
		
		private boolean
		dependsOnNameEtc()
		{
			return( depends_on_names_etc );
		}
		
		private String
		removeComments(
			String	str )
		{
			String[] lines = str.trim().split( "\n" );
			
			String result = "";
			
			for ( String line: lines ){
				
				line = line.trim();
				
				if ( line.startsWith( "#" ) || line.startsWith( "//" )) {
					
				}else{
					
					result += line + "\n";
				}
			}
			
			return( result.trim());
		}
		
		private ConstraintExpr
		compileStart(
			String						str,
			Map<String,ConstraintExpr>	context )
		{
			str = removeComments( str );
			
			if ( str.equalsIgnoreCase( "true" )){

				return( new ConstraintExprTrue());
			}

			char[] chars = str.toCharArray();

			boolean	in_quote 		= false;
			boolean prev_was_esc	= false;
			
			int	level 			= 0;
			int	bracket_start 	= 0;

			StringBuilder result = new StringBuilder( str.length());

			for ( int i=0;i<chars.length;i++){

				char c = chars[i];
				
				boolean is_esc = c == '\\';
				
				if ( is_esc && prev_was_esc ){
					
					prev_was_esc = false;
					
					continue;
				}
				
				if ( GeneralUtils.isDoubleQuote( c )){

					if ( !prev_was_esc ){

						in_quote = !in_quote;
					}
				}

				prev_was_esc = is_esc;
				
				if ( !in_quote ){

					if ( c == '(' ){

						level++;

						if ( level == 1 ){

							bracket_start = i+1;
						}
					}else if ( c == ')' ){

						level--;

						if ( level == 0 ){

							String bracket_text = new String( chars, bracket_start, i-bracket_start ).trim();

							//This appears to be written for a purpose, but we can't find a use case, so it's removed until someone complains we broke something
							//bracket_text = bracket_text.replaceAll( "\\Q\\\\\\E", Matcher.quoteReplacement( "\\" ));
							
							if ( result.length() > 0 && Character.isLetterOrDigit( result.charAt( result.length()-1 ))){

									// function call

								String key = "{" + context.size() + "}";

								context.put( key, new ConstraintExprParams( bracket_text, context ));

								result.append( "(" ).append( key ).append( ")" );

							}else{

								ConstraintExpr sub_expr = compileStart( bracket_text, context );

								if ( sub_expr == null ){
									
									throw( new RuntimeException( "Failed to compile '" + bracket_text + "'" ));
								}
								
								String key = "{" + context.size() + "}";

								context.put(key, sub_expr );

								result.append( key );
							}
						}
					}else if ( level == 0 ){

						if ( !Character.isWhitespace( c )){

							result.append( c );
						}
					}
				}else if ( level == 0 ){

					result.append( c );

				}
			}

			if ( in_quote ){

				throw( new RuntimeException( "Unmatched '\"' in \"" + str + "\"" ));
			}

			if ( level != 0 ){

				throw( new RuntimeException( "Unmatched '(' in \"" + str + "\"" ));
			}

			return( compileBasic( result.toString(), context ));
		}

		private ConstraintExpr
		compileBasic(
			String						str,
			Map<String,ConstraintExpr>	context )
		{			
			if ( str.contains( "||" )){

				String[] bits = str.split( "\\|\\|" );

				return( new ConstraintExprOr( compile( bits, context )));

			}else if ( str.contains( "&&" )){

				String[] bits = str.split( "&&" );

				return( new ConstraintExprAnd( compile( bits, context )));

			}else if ( str.contains( "^" )){

				String[] bits = str.split( "\\^" );

				return( new ConstraintExprXor( compile( bits, context )));

			}else{
							
				Matcher m = comp_op_pattern.matcher( str );

				if ( m.find()){
							
					String lhs 	= m.group(1).trim();
					String op 	= m.group(2).trim();
					String rhs	= m.group(3).trim();
					
					ConstraintExprParams params = new ConstraintExprParams( lhs + "," + rhs, context );
					
					return( new ConstraintExprFunction( comp_op_map.get( op ), params ));
					
				}else{
					m = comp_ift_pattern.matcher( str );

					if ( m.find()){
								
						String op1 	= m.group(1).trim();
						String op2 	= m.group(2).trim();
						String op3	= m.group(3).trim();
						
						ConstraintExprParams params = new ConstraintExprParams( op1 + "," + op2 + "," + op3, context );
						
						return( new ConstraintExprFunction( "ifThenElse", params ));
						
					}else if ( str.startsWith( "!" )){
		
						return( new ConstraintExprNot( compileBasic( str.substring(1).trim(), context )));
		
					}else if ( str.startsWith( "{" )){
		
						ConstraintExpr val = context.get( str );
							
						if ( val == null ){
							
							throw( new RuntimeException( "Failed to compile '" + str + "'" ));
						}
							
						return( val );
		 
					}else{
		
						int	pos = str.indexOf( '(' );
		
						if ( pos > 0 && str.endsWith( ")" )){
		
							String func = str.substring( 0, pos );
		
							String key = str.substring( pos+1, str.length() - 1 ).trim();
		
							ConstraintExprParams params = (ConstraintExprParams)context.get( key );
		
							return( new ConstraintExprFunction( func, params ));
		
						}else{
		
							throw( new RuntimeException( "Unsupported construct: " + str ));
						}
					}
			}
			}
		}

		private ConstraintExpr[]
		compile(
			String[]					bits,
			Map<String,ConstraintExpr>	context )
		{
			ConstraintExpr[] res = new ConstraintExpr[ bits.length ];

			for ( int i=0; i<bits.length;i++){

				res[i] = compileBasic( bits[i].trim(), context );
			}

			return( res );
		}

		private String
		getConstraint()
		{
			return( constraint );
		}

		private String
		getOptions()
		{
			if ( auto_add ){
				if ( auto_remove ){
					return( "am=0;" );
				}else{
					return( "am=1;" );
				}
			}else if ( auto_remove ){
				return( "am=2;" );
			}else if ( new_only ){
				return( "am=3" );
			}else{
				Debug.out(  "Hmm" );
				
				return( "am=0;" );
			}
		}

		private void
		apply(
			DownloadManager			dm,
			boolean					is_new )
		{
			if ( ignoreDownload( dm )){

				return;
			}

			if ( expr == null ){

				return;
			}

			if ( handler.isStopping()){

				return;
			}

			if ( !canProcess( this, dm, is_new )){

				return;
			}

			Set<Taggable>	existing = tag_maybe_null.getTagged();

			applySupport( existing, dm, is_new );
		}

		private void
		apply(
			List<DownloadManager>	dms )
		{
			if ( expr == null ){

				return;
			}

			if ( handler.isStopping()){

				return;
			}

			if ( !canProcess( this, dms )){

				return;
			}

			Set<Taggable>	existing = tag_maybe_null.getTagged();

			for ( DownloadManager dm: dms ){

				if ( handler.isStopping()){

					return;
					
				}else  if ( ignoreDownload( dm )){

					continue;
				}

				applySupport( existing, dm, false );
			}
		}

		private void
		applySupport(
			Set<Taggable>		existing,
			DownloadManager		dm,
			boolean				is_new )
		{
			applySupport2( existing, dm, must_check_dependencies, null, is_new );
		}
		
		private void
		applySupport2(
			Set<Taggable>		existing,
			DownloadManager		dm,
			boolean				check_dependencies,
			Set<TagConstraint>	checked,
			boolean				is_new )
		{
			if ( check_dependencies && checked != null && checked.contains( this )){
				
				return;
			}
			
			boolean	do_add = auto_add;
			
			if ( new_only ){
				
				if ( is_new ){
					
					do_add = true;
					
				}else{
					
					return;
				}
			}
			
			if ( testConstraint( dm, null )){

				if ( do_add ){

					if ( !existing.contains( dm )){


						if ( check_dependencies && dependent_on_tags != null ){
						
							boolean	recheck = false;
							
							for ( Tag t: dependent_on_tags ){
								
								TagConstraint dep = handler.constrained_tags.get( t );
								
								if ( dep != null ){
									
									if ( checked == null ){
										
										checked = new HashSet<>();
									}
									
									try{
										checked.add( this );
										
										//System.out.println( "checking sub-dep " + dep + ", checked=" + checked );
										
										dep.applySupport2( existing, dm, true, checked, is_new );
									
									}finally{
										
										checked.remove( this );
									}
									
									recheck = true;
								}
							}
							
							if ( recheck ){
								
								applySupport2( existing, dm, false, checked, is_new );
									
								return;
							}
						}
						
						if ( handler.isStopping()){

							return;
						}

						if ( canAddTaggable( dm )){

							tag_maybe_null.addTaggable( dm );
						}
					}
				}
			}else{

				if ( auto_remove ){

					if ( existing.contains( dm )){

						if ( handler.isStopping()){

							return;
						}

						if ( tag_maybe_null.hasTaggable( dm )){
						
							tag_maybe_null.removeTaggable( dm );
						}
					}
				}
			}
		}
		
		private boolean
		ignoreDownload(
			DownloadManager dm )
		{
			if ( dm.isDestroyed()) {
				
				return( true );
				
				// 2018/10/25 - can't think of any good reason to skip non-persistent downloads, other tag
				// operations don't
				
			//}else if ( !dm.isPersistent()) {
			
				//return( !dm.getDownloadState().getFlag(DownloadManagerState.FLAG_METADATA_DOWNLOAD ));
				
			}else{
				
				return( false );
			}
		}

		private boolean
		canAddTaggable(
			DownloadManager		dm )
		{
			long	now = SystemTime.getMonotonousTime();

			Map<DownloadManager,Long> recent_dms = handler.apply_history.get( tag_maybe_null );

			if ( recent_dms != null ){

				Long time = recent_dms.get( dm );

				if ( time != null && now - time < 1000 ){

					System.out.println( "Not applying constraint as too recently actioned: " + dm.getDisplayName() + "/" + tag_maybe_null.getTagName( true ));

					return( false );
				}
			}

			if ( recent_dms == null ){

				recent_dms = new IdentityHashMap<>();

				handler.apply_history.put( tag_maybe_null, recent_dms );
			}

			recent_dms.put( dm, now );

			return( true );
		}

		public boolean
		testConstraint(
			DownloadManager		dm )
		{
			return( testConstraint( dm, null ));
		}
		
		private boolean
		testConstraint(
			DownloadManager		dm,
			StringBuilder		debug )
		{
			if ( enabled ){
								
				activity_average.addValue( 1 );
				
				List<Tag> dm_tags = handler.tag_manager.getTagsForTaggable( dm );
	
				if ( expr == null ){
				
					if ( debug!=null){
						debug.append( "false");
					}
					
					return( false );
				}
				
				Map<String,Object>	context = new HashMap<>();
				
				Object o_result = expr.eval( context, dm, dm_tags, debug );
				
				if ( o_result instanceof Number ){
					
					o_result = ((Number)o_result).intValue() != 0;
				}
				
				if ( o_result instanceof Boolean ){
				
					boolean result = (Boolean)o_result;
					
					if ( result && tag_maybe_null != null ){
						
						long[] colours = (long[])context.get( EVAL_CTX_COLOURS );
						
						tag_maybe_null.setColors( colours );
						
						Object[] tag_sort = (Object[])context.get( EVAL_CTX_TAG_SORT );
						
						if ( tag_sort != null ){
							
							long current = ((Number)tag_sort[0]).longValue();
									
							long uid = tag_maybe_null.getTagUID();
							
							String options;
							
							if ( tag_sort.length > 1 ){
								
								options = (String)tag_sort[1];
								
							}else{
								
								options = null;
							}
	
							Map<Long,Object[]>	map = (Map<Long,Object[]>)dm.getDownloadState().getTransientAttribute( DownloadManagerState.AT_TRANSIENT_TAG_SORT );
													
							boolean changed = false;
							
							if ( map == null ){
								
								map = new HashMap<>();
								
								map.put( uid, new Object[]{ tag_maybe_null, current, options });
								
								changed = true;
								
							}else{
								
								Object[] existing = (Object[])map.get( uid );
								
								if ( existing != null ){
									
										// no change to map
									
									existing[1] = current;
									existing[2]	= options;
									
								}else{
									
									map = new HashMap<>( map );	// copy on write
									
									map.put( uid, new Object[]{ tag_maybe_null, current, options });
									
									changed = true;
								}
							}
							
							if ( changed ){
								
								dm.getDownloadState().setTransientAttribute( DownloadManagerState.AT_TRANSIENT_TAG_SORT, map );
							}
						}
					}
									
					return( result );
					
				}else{
					
					setError( "Constraint must evaluate to a boolean" );
					
					return( false );
				}
			}else{
				
				if ( debug != null ){
					
					debug.append( "Constraint is not enabled" );
				}
				
				return( false );
			}
		}

		private interface
		ConstraintExpr
		{
			public Object
			eval(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				StringBuilder		debug );

			public String
			getString();
		}

		private static class
		ConstraintExprTrue
			implements ConstraintExpr
		{
			@Override
			public Object
			eval(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				StringBuilder		debug )
			{
				if ( debug != null ){
					
					debug.append( getString());
				}
				
				return( true );
			}

			@Override
			public String
			getString()
			{
				return( "true" );
			}
		}

		private class
		ConstraintExprParams
			implements  ConstraintExpr
		{
			private final String						value;
			private final Map<String,ConstraintExpr>	context;
			
			private
			ConstraintExprParams(
				String						_value,
				Map<String,ConstraintExpr>	_context )
			{
				value		= _value.trim();
				context		= _context;
				
				try{
					Object[] args = getValues();
					
					for ( Object obj: args ){
						
						if ( obj instanceof String ){
							
							int[] kw_details = keyword_map.get((String)obj);
							
							if ( kw_details != null ){
								
								depends_on_level = Math.max( depends_on_level, kw_details[1] );
							}
						}
					}
				}catch( Throwable e ){
				}
			}

			@Override
			public Object
			eval(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				StringBuilder		debug )
			{
				return( false );
			}

			public Object[]
			getValues()
			{
				if ( value.length() == 0 ){

					return( new String[0]);
					
				}else if ( !value.contains( "," )){

						// guaranteed single argument
					
					if ( GeneralUtils.startsWithDoubleQuote( value )){ 
						
						// string literal
						
					}else if ( value.startsWith( "{" )){
						
						Object temp = dereference( value );
						
						if ( temp instanceof Object[]){
							
							return((Object[])temp);
							
						}else{
							
							return( new Object[]{ temp });
						}
						
					}else if ( value.contains( "(" ) || comp_op_pattern.matcher( value ).find()){
						
						return( new Object[]{  compileStart(value, context )});
					}

					return( new Object[]{ value });

				}else{

					char[]	chars = value.toCharArray();

					boolean in_quote		= false;
					int		bracket_level	= 0;
					
					List<Object>	params = new ArrayList<>(16);

					StringBuilder current_param = new StringBuilder( value.length());

					for (int i=0;i<chars.length;i++){

						char c = chars[i];

						if ( GeneralUtils.isDoubleQuote( c )){

							if ( i == 0 || chars[i-1] != '\\' ){

								in_quote = !in_quote;
							}
						}

						if ( c == ',' && !in_quote ){

							if ( bracket_level == 0 ){
								
								params.add( current_param.toString());
	
								current_param.setLength( 0 );
								
							}else{
								
								current_param.append( c );
							}
						}else{

							if ( !in_quote ){
								
								if ( c == '(' ){
									
									bracket_level++;
									
								}else if ( c == ')' ){
									
									bracket_level--;
								}
							}
							
							if ( in_quote || !Character.isWhitespace( c )){

								current_param.append( c );
							}
						}
					}

					params.add( current_param.toString());

					for ( int i=0;i<params.size();i++){
						
						String p = (String)params.get( i );
						
						if ( GeneralUtils.startsWithDoubleQuote( p )){
							
							// string literal
							
						}else if ( p.startsWith( "{" )){
							
							params.set(i, dereference( p ));
							
						}else if ( p.contains( "(" ) || comp_op_pattern.matcher( p ).find()){
							
							params.set(i,compileStart(p, context ));
						}
					}
					
					return( params.toArray( new Object[ params.size()]));
				}
			}

			private Object
			dereference(
				String	key )
		
			{
				ConstraintExpr expr = context.get( key );
				
				if ( expr == null ){
					
					throw( new RuntimeException( "Reference " + key + " not found" ));
				}
			
				if ( expr instanceof ConstraintExprParams ){
					
					ConstraintExprParams params = (ConstraintExprParams)expr;
					
					return( params.getValues());
				}
				
				return( expr );
			}
			
			@Override
			public String
			getString()
			{
				Object[] params = getValues();
				
				String str = "";
				
				for ( Object obj: params ){
					
					str += (str.isEmpty()?"":",") + (obj instanceof ConstraintExpr?((ConstraintExpr)obj).getString():obj);
				}
				
				return( str );
			}
		}

		private static class
		ConstraintExprNot
			implements  ConstraintExpr
		{
			private final ConstraintExpr expr;

			private
			ConstraintExprNot(
				ConstraintExpr	e )
			{
				expr = e;
			}

			@Override
			public Object
			eval(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				StringBuilder		debug )
			{
				if ( debug != null ){
					debug.append( "!(" );
				}
				
				Boolean result = !(Boolean)expr.eval( context, dm, tags, debug );
				
				if ( debug != null ){
					debug.append( ")" );
					debug.append( "->" );
					debug.append( result );
				}
				
				return( result );
			}

			@Override
			public String
			getString()
			{
				return( "!(" + expr.getString() + ")");
			}
		}

		private static class
		ConstraintExprOr
			implements  ConstraintExpr
		{
			private final ConstraintExpr[]	exprs;

			private
			ConstraintExprOr(
				ConstraintExpr[]	_exprs )
			{
				exprs = _exprs;
			}

			@Override
			public Object
			eval(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				StringBuilder		debug )
			{
				if ( debug != null ){
					debug.append( "(" );
				}
				
				boolean res = false;
				
				try{
					int	num = 1;
					
					for ( ConstraintExpr expr: exprs ){
	
						if ( debug != null ){
							if ( num > 1 ){
								debug.append( "||");
							}
						}
						
						Boolean b = (Boolean)expr.eval( context, dm, tags, debug );
						
						//if ( debug != null ){
						//	debug.append( "->" );
						//	debug.append( b );
						//}
						
						if ( b ){
	
							res = true;
							
							return( true );
						}
						
						num++;
					}
					
					res = false;
					
					return( false );
					
				}finally{
					if ( debug != null ){
						debug.append( ")" );
						debug.append( "->" );
						debug.append( res );
					}
				}
			}

			@Override
			public String
			getString()
			{
				String res = "";

				for ( int i=0;i<exprs.length;i++){

					res += (i==0?"":"||") + exprs[i].getString();
				}

				return( "(" + res + ")" );
			}
		}

		private static class
		ConstraintExprAnd
			implements  ConstraintExpr
		{
			private final ConstraintExpr[]	exprs;

			private
			ConstraintExprAnd(
				ConstraintExpr[]	_exprs )
			{
				exprs = _exprs;
			}

			@Override
			public Object
			eval(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				StringBuilder		debug )
			{
				if ( debug != null ){
					debug.append( "(" );
				}
				
				boolean res = false;
				
				try{
					int	num = 1;

					for ( ConstraintExpr expr: exprs ){
	
						if ( debug != null ){
							if ( num > 1 ){
								debug.append( "&&");
							}
						}
						
						boolean b = (Boolean)expr.eval( context, dm, tags, debug );
						
						//if ( debug != null ){
						//	debug.append( "->" );
						//	debug.append( b );
						//}

						if ( !b ){
	
							res = false;
							
							return( false );
						}
						
						num++;
					}
	
					res = true;
					
					return( true );
				}finally{
					if ( debug != null ){
						debug.append( ")" );
						debug.append( "->" );
						debug.append( res );
					}
				}
			}

			@Override
			public String
			getString()
			{
				String res = "";

				for ( int i=0;i<exprs.length;i++){

					res += (i==0?"":"&&") + exprs[i].getString();
				}

				return( "(" + res + ")" );
			}
		}

		private static class
		ConstraintExprXor
			implements  ConstraintExpr
		{
			private final ConstraintExpr[]	exprs;

			private
			ConstraintExprXor(
				ConstraintExpr[]	_exprs )
			{
				exprs = _exprs;

				if ( exprs.length < 2 ){

					throw( new RuntimeException( "Two or more arguments required for ^" ));
				}
			}

			@Override
			public Object
			eval(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				StringBuilder		debug )
			{
				if ( debug != null ){
					debug.append( "(" );
				}

				boolean res = false;
				
				try{
					res = (Boolean)exprs[0].eval( context, dm, tags, debug );
	
					if ( debug != null ){
						debug.append( res );
					}
					
					for ( int i=1;i<exprs.length;i++){
	
						if ( debug != null ){
							debug.append( "^" );
						}

						boolean b = (Boolean)exprs[i].eval( context, dm, tags, debug );
						
						//if ( debug != null ){
						//	debug.append( "->" );
						//	debug.append( b );
						//}
						
						res = res ^ b;
					}
	
					return( res );
					
				}finally{
					if ( debug != null ){
						debug.append( ")" );
						debug.append( "->" );
						debug.append( res );
					}
				}
			}

			@Override
			public String
			getString()
			{
				String res = "";

				for ( int i=0;i<exprs.length;i++){

					res += (i==0?"":"^") + exprs[i].getString();
				}

				return( "(" + res + ")" );
			}
		}
		
		static final Map<String,Integer>	fn_map = new HashMap<>();

		private static final int FT_HAS_TAG		= 1;
		private static final int FT_IS_PRIVATE	= 2;

		private static final int FT_GE			= 3;
		private static final int FT_GT			= 4;
		private static final int FT_LE			= 5;
		private static final int FT_LT			= 6;
		private static final int FT_EQ			= 7;
		private static final int FT_NEQ			= 8;

		private static final int FT_CONTAINS	= 9;
		private static final int FT_MATCHES		= 10;

		private static final int FT_HAS_NET			= 11;
		private static final int FT_IS_COMPLETE		= 12;
		private static final int FT_CAN_ARCHIVE		= 13;
		private static final int FT_IS_FORCE_START	= 14;
		private static final int FT_JAVASCRIPT		= 15;
		private static final int FT_IS_CHECKING		= 16;
		private static final int FT_IS_STOPPED		= 17;
		private static final int FT_IS_PAUSED		= 18;
		private static final int FT_IS_ERROR		= 19;
		private static final int FT_IS_MAGNET		= 20;
		private static final int FT_IS_LOW_NOISE	= 21;
		private static final int FT_COUNT_TAG		= 22;
		private static final int FT_HAS_TAG_GROUP	= 23;
		private static final int FT_HOURS_TO_SECS	= 24;
		private static final int FT_DAYS_TO_SECS	= 25;
		private static final int FT_WEEKS_TO_SECS	= 26;
		private static final int FT_GET_CONFIG		= 27;
		private static final int FT_HAS_TAG_AGE		= 28;
		private static final int FT_LOWERCASE		= 29;
		private static final int FT_SET_COLOURS		= 30;
		private static final int FT_IS_NEW			= 31;
		private static final int FT_IS_SUPER_SEEDING	= 32;
		private static final int FT_IS_SEQUENTIAL		= 33;
		private static final int FT_TAG_POSITION		= 34;
		private static final int FT_IS_SHARE			= 35;
		private static final int FT_IS_UNALLOCATED		= 36;
		private static final int FT_IS_QUEUED			= 37;
		private static final int FT_IS_IP_FILTERED		= 38;
		private static final int FT_COUNT_TRACKERS		= 39;
		private static final int FT_IS_MOVING			= 40;
		private static final int FT_SET_TAG_SORT		= 41;
		private static final int FT_TIME_TO_ELAPSED		= 42;
		private static final int FT_TO_MB				= 43;
		private static final int FT_TO_MiB				= 44;
		private static final int FT_TO_GB				= 45;
		private static final int FT_TO_GiB				= 46;
		private static final int FT_PLUS				= 47;
		private static final int FT_MINUS				= 48;
		private static final int FT_MULT				= 49;
		private static final int FT_DIV					= 50;
		private static final int FT_GET_TAG_WEIGHT		= 51;
		private static final int FT_IF_THEN_ELSE		= 52;
		private static final int FT_IS_SEEDING			= 53;
		private static final int FT_IS_DOWNLOADING		= 54;
		private static final int FT_IS_RUNNING			= 55;
		private static final int FT_REM					= 56;
		private static final int FT_MIN					= 57;
		private static final int FT_MAX					= 58;
		private static final int FT_GET_TAG_SORT		= 59;
		private static final int FT_LENGTH				= 60;
		private static final int FT_COUNT				= 61;
		private static final int FT_TRACKER_PEERS		= 62;
		private static final int FT_TRACKER_SEEDS		= 63;
		private static final int FT_PLUGIN_OPTION		= 64;

		static{
			fn_map.put( "hastag", FT_HAS_TAG );
			fn_map.put( "isprivate", FT_IS_PRIVATE );
			fn_map.put( "isge", FT_GE );
			fn_map.put( "isgt", FT_GT );
			fn_map.put( "isle", FT_LE );
			fn_map.put( "islt", FT_LT );
			fn_map.put( "iseq", FT_EQ );
			fn_map.put( "isneq", FT_NEQ );
			fn_map.put( "contains", FT_CONTAINS );
			fn_map.put( "matches", FT_MATCHES );
			fn_map.put( "hasnet", FT_HAS_NET );
			fn_map.put( "iscomplete", FT_IS_COMPLETE );
			fn_map.put( "canarchive", FT_CAN_ARCHIVE );
			fn_map.put( "isforcestart", FT_IS_FORCE_START );
			fn_map.put( "javascript", FT_JAVASCRIPT );
			fn_map.put( "ischecking", FT_IS_CHECKING );
			fn_map.put( "isstopped", FT_IS_STOPPED );
			fn_map.put( "ispaused", FT_IS_PAUSED );
			fn_map.put( "isseeding", FT_IS_SEEDING );
			fn_map.put( "isdownloading", FT_IS_DOWNLOADING );
			fn_map.put( "isrunning", FT_IS_RUNNING );
			fn_map.put( "iserror", FT_IS_ERROR );
			fn_map.put( "ismagnet", FT_IS_MAGNET );
			fn_map.put( "islownoise", FT_IS_LOW_NOISE );
			fn_map.put( "counttag", FT_COUNT_TAG );
			fn_map.put( "hastaggroup", FT_HAS_TAG_GROUP );
			
			fn_map.put( "hourstoseconds", FT_HOURS_TO_SECS );
			fn_map.put( "htos", FT_HOURS_TO_SECS );
			fn_map.put( "h2s", FT_HOURS_TO_SECS );
			
			fn_map.put( "daystoseconds", FT_DAYS_TO_SECS );
			fn_map.put( "dtos", FT_DAYS_TO_SECS );
			fn_map.put( "d2s", FT_DAYS_TO_SECS );
			
			fn_map.put( "weekstoseconds", FT_WEEKS_TO_SECS );
			fn_map.put( "wtos", FT_WEEKS_TO_SECS );
			fn_map.put( "w2s", FT_WEEKS_TO_SECS );
			
			fn_map.put( "getconfig", FT_GET_CONFIG );
			fn_map.put( "hastagage", FT_HAS_TAG_AGE );
			fn_map.put( "lowercase", FT_LOWERCASE );
			
			fn_map.put( "setcolours", FT_SET_COLOURS );
			fn_map.put( "setcolors", FT_SET_COLOURS );
			
			fn_map.put( "isnew", FT_IS_NEW );
			fn_map.put( "issuperseeding", FT_IS_SUPER_SEEDING );
			fn_map.put( "issequential", FT_IS_SEQUENTIAL );
			fn_map.put( "tagposition", FT_TAG_POSITION );
			fn_map.put( "isshare", FT_IS_SHARE );
			fn_map.put( "isunallocated", FT_IS_UNALLOCATED );
			fn_map.put( "isqueued", FT_IS_QUEUED );
			fn_map.put( "isipfiltered", FT_IS_IP_FILTERED );
			fn_map.put( "counttrackers", FT_COUNT_TRACKERS );
			fn_map.put( "ismoving", FT_IS_MOVING );
			fn_map.put( "settagsort", FT_SET_TAG_SORT );
			fn_map.put( "timetoelapsed", FT_TIME_TO_ELAPSED );
			fn_map.put( "tomb", FT_TO_MB );
			fn_map.put( "tomib", FT_TO_MiB );
			fn_map.put( "togb", FT_TO_GB );
			fn_map.put( "togib", FT_TO_GiB );
			fn_map.put( "plus", FT_PLUS );
			fn_map.put( "minus", FT_MINUS );
			fn_map.put( "mult", FT_MULT );
			fn_map.put( "div", FT_DIV );
			fn_map.put( "rem", FT_REM );
			fn_map.put( "min", FT_MIN );
			fn_map.put( "max", FT_MAX );
			fn_map.put( "gettagweight", FT_GET_TAG_WEIGHT );
			fn_map.put( "ifthenelse", FT_IF_THEN_ELSE );
			fn_map.put( "gettagsort", FT_GET_TAG_SORT );
			fn_map.put( "length", FT_LENGTH );
			fn_map.put( "count", FT_COUNT );
			
			fn_map.put( "trackerpeers", FT_TRACKER_PEERS );
			fn_map.put( "trackerseeds", FT_TRACKER_SEEDS );
			
			fn_map.put( "pluginoption", FT_PLUGIN_OPTION );
		}
		
		private static final int	DEP_STATIC		= 0;
		private static final int	DEP_RUNNING		= 1;
		private static final int	DEP_TIME		= 2;
		
		static final Map<String,int[]>	keyword_map = new HashMap<>();

		private static final int	KW_SHARE_RATIO		= 0;
		private static final int	KW_AGE 				= 1;
		private static final int	KW_PERCENT 			= 2;
		private static final int	KW_DOWNLOADING_FOR 	= 3;
		private static final int	KW_SEEDING_FOR 		= 4;
		private static final int	KW_SWARM_MERGE 		= 5;
		private static final int	KW_LAST_ACTIVE 		= 6;
		private static final int	KW_SEED_COUNT 		= 7;
		private static final int	KW_PEER_COUNT 		= 8;
		private static final int	KW_SEED_PEER_RATIO 	= 9;
		private static final int	KW_RESUME_IN 		= 10;
		private static final int	KW_MIN_OF_HOUR 		= 11;
		private static final int	KW_HOUR_OF_DAY 		= 12;
		private static final int	KW_DAY_OF_WEEK 		= 13;
		private static final int	KW_TAG_AGE 			= 14;
		private static final int	KW_COMPLETED_AGE 	= 15;
		private static final int	KW_PEER_MAX_COMP 	= 16;
		private static final int	KW_PEER_AVERAGE_COMP 	= 17;
		private static final int	KW_LEECHER_MAX_COMP 	= 18;
		private static final int	KW_SIZE				 	= 19;
		private static final int	KW_SIZE_MB			 	= 20;
		private static final int	KW_SIZE_GB			 	= 21;
		private static final int	KW_FILE_COUNT		 	= 22;
		private static final int	KW_AVAILABILITY		 	= 23;
		private static final int	KW_UP_IDLE			 	= 24;
		private static final int	KW_DOWN_IDLE		 	= 25;
		private static final int	KW_DOWNLOADED		 	= 26;
		private static final int	KW_UPLOADED			 	= 27;
		private static final int	KW_NAME				 	= 28;
		private static final int	KW_FILE_NAMES		 	= 29;
		private static final int	KW_SAVE_PATH		 	= 30;
		private static final int	KW_SAVE_FOLDER		 	= 31;
		private static final int	KW_MAX_UP			 	= 32;
		private static final int	KW_MAX_DOWN			 	= 33;
		private static final int	KW_FILE_NAMES_SELECTED	= 34;
		private static final int	KW_FILE_EXTS			= 35;
		private static final int	KW_FILE_EXTS_SELECTED	= 36;
		private static final int	KW_TORRENT_TYPE			= 37;
		private static final int	KW_FILE_PATHS			= 38;
		private static final int	KW_FILE_PATHS_SELECTED	= 39;
		private static final int	KW_TARGET_RATIO			= 40;
		private static final int	KW_TAG_NAMES			= 41;
		private static final int	KW_TRACKER_STATUS		= 42;
		private static final int	KW_FULL_COPY_SEEN		= 43;
		private static final int	KW_REMAINING			= 44;
		private static final int	KW_DOWN_SPEED			= 45;
		private static final int	KW_UP_SPEED				= 46;
		private static final int	KW_SESSION_AGE			= 47;
		private static final int	KW_PLUGIN_MY_RATING		= 48;
		private static final int	KW_MAX32				= 49;
		private static final int	KW_MIN32				= 50;
		private static final int	KW_MAX64				= 51;
		private static final int	KW_MIN64				= 52;
		private static final int	KW_MOC_PATH				= 53;
		private static final int	KW_FILE_COUNT_SELECTED	= 54;
		private static final int	KW_TRACKERS				= 55;

		static{
			keyword_map.put( "shareratio", 				new int[]{KW_SHARE_RATIO,			DEP_RUNNING });
			keyword_map.put( "share_ratio", 			new int[]{KW_SHARE_RATIO,			DEP_RUNNING });
			keyword_map.put( "age",						new int[]{KW_AGE,					DEP_TIME });
			keyword_map.put( "percent", 				new int[]{KW_PERCENT,				DEP_RUNNING });
			keyword_map.put( "downloadingfor", 			new int[]{KW_DOWNLOADING_FOR,		DEP_RUNNING });
			keyword_map.put( "downloading_for", 		new int[]{KW_DOWNLOADING_FOR,		DEP_RUNNING });
			keyword_map.put( "seedingfor", 				new int[]{KW_SEEDING_FOR,			DEP_RUNNING });
			keyword_map.put( "seeding_for", 			new int[]{KW_SEEDING_FOR,			DEP_RUNNING });
			keyword_map.put( "swarmmergebytes", 		new int[]{KW_SWARM_MERGE,			DEP_RUNNING });
			keyword_map.put( "swarm_merge_bytes", 		new int[]{KW_SWARM_MERGE,			DEP_RUNNING });
			keyword_map.put( "lastactive", 				new int[]{KW_LAST_ACTIVE,			DEP_RUNNING });
			keyword_map.put( "last_active", 			new int[]{KW_LAST_ACTIVE,			DEP_RUNNING });
			keyword_map.put( "seedcount", 				new int[]{KW_SEED_COUNT,			DEP_TIME  });
			keyword_map.put( "seed_count", 				new int[]{KW_SEED_COUNT,			DEP_TIME });
			keyword_map.put( "peercount", 				new int[]{KW_PEER_COUNT,			DEP_TIME });
			keyword_map.put( "peer_count", 				new int[]{KW_PEER_COUNT,			DEP_TIME });
			keyword_map.put( "seedpeerratio", 			new int[]{KW_SEED_PEER_RATIO,		DEP_TIME });
			keyword_map.put( "seed_peer_ratio", 		new int[]{KW_SEED_PEER_RATIO,		DEP_TIME });
			keyword_map.put( "resumein", 				new int[]{KW_RESUME_IN,				DEP_TIME });
			keyword_map.put( "resume_in",				new int[]{KW_RESUME_IN,				DEP_TIME });

			keyword_map.put( "minofhour", 				new int[]{KW_MIN_OF_HOUR,			DEP_TIME });
			keyword_map.put( "min_of_hour",				new int[]{KW_MIN_OF_HOUR,			DEP_TIME });
			keyword_map.put( "hourofday", 				new int[]{KW_HOUR_OF_DAY,			DEP_TIME });
			keyword_map.put( "hour_of_day", 			new int[]{KW_HOUR_OF_DAY,			DEP_TIME });
			keyword_map.put( "dayofweek", 				new int[]{KW_DAY_OF_WEEK,			DEP_TIME });
			keyword_map.put( "day_of_week", 			new int[]{KW_DAY_OF_WEEK,			DEP_TIME });
			keyword_map.put( "tagage", 					new int[]{KW_TAG_AGE,				DEP_TIME });
			keyword_map.put( "tag_age", 				new int[]{KW_TAG_AGE,				DEP_TIME });
			keyword_map.put( "completedage", 			new int[]{KW_COMPLETED_AGE,			DEP_TIME });
			keyword_map.put( "completed_age", 			new int[]{KW_COMPLETED_AGE,			DEP_TIME });

			keyword_map.put( "peermaxcompletion", 		new int[]{KW_PEER_MAX_COMP,			DEP_RUNNING });
			keyword_map.put( "peer_max_completion", 	new int[]{KW_PEER_MAX_COMP,			DEP_RUNNING });
			
			keyword_map.put( "leechmaxcompletion", 		new int[]{KW_LEECHER_MAX_COMP,		DEP_RUNNING });
			keyword_map.put( "leech_max_completion", 	new int[]{KW_LEECHER_MAX_COMP,		DEP_RUNNING });
			keyword_map.put( "leechermaxcompletion", 	new int[]{KW_LEECHER_MAX_COMP,		DEP_RUNNING });
			keyword_map.put( "leecher_max_completion", 	new int[]{KW_LEECHER_MAX_COMP,		DEP_RUNNING });
			
			keyword_map.put( "peeraveragecompletion", 	new int[]{KW_PEER_AVERAGE_COMP,		DEP_RUNNING });
			keyword_map.put( "peer_average_completion", new int[]{KW_PEER_AVERAGE_COMP,		DEP_RUNNING });
			
			keyword_map.put( "size", 					new int[]{KW_SIZE,					DEP_STATIC });
			keyword_map.put( "sizemb", 					new int[]{KW_SIZE_MB,				DEP_STATIC });
			keyword_map.put( "size_mb", 				new int[]{KW_SIZE_MB,				DEP_STATIC });
			keyword_map.put( "sizegb", 					new int[]{KW_SIZE_GB,				DEP_STATIC });
			keyword_map.put( "size_gb", 				new int[]{KW_SIZE_GB,				DEP_STATIC });
			
			keyword_map.put( "filecount", 				new int[]{KW_FILE_COUNT,			DEP_STATIC });
			keyword_map.put( "file_count", 				new int[]{KW_FILE_COUNT,			DEP_STATIC });
			
			keyword_map.put( "availability", 			new int[]{KW_AVAILABILITY,			DEP_RUNNING });
			
			keyword_map.put( "upidle", 					new int[]{KW_UP_IDLE,				DEP_RUNNING });
			keyword_map.put( "up_idle", 				new int[]{KW_UP_IDLE,				DEP_RUNNING });
			keyword_map.put( "downidle", 				new int[]{KW_DOWN_IDLE,				DEP_RUNNING });
			keyword_map.put( "down_idle", 				new int[]{KW_DOWN_IDLE, 			DEP_RUNNING });

			keyword_map.put( "downloaded", 				new int[]{KW_DOWNLOADED,			DEP_RUNNING });
			keyword_map.put( "uploaded", 				new int[]{KW_UPLOADED,				DEP_RUNNING });
			
			keyword_map.put( "name", 					new int[]{KW_NAME,					DEP_STATIC });
			keyword_map.put( "filenames", 				new int[]{KW_FILE_NAMES,			DEP_STATIC });
			keyword_map.put( "file_names", 				new int[]{KW_FILE_NAMES,			DEP_STATIC });
			keyword_map.put( "filenamesselected",		new int[]{KW_FILE_NAMES_SELECTED,	DEP_STATIC });
			keyword_map.put( "file_names_selected",		new int[]{KW_FILE_NAMES_SELECTED,	DEP_STATIC });
			keyword_map.put( "fileexts",				new int[]{KW_FILE_EXTS,				DEP_STATIC });
			keyword_map.put( "file_exts",				new int[]{KW_FILE_EXTS,				DEP_STATIC });
			keyword_map.put( "fileextsselected",		new int[]{KW_FILE_EXTS_SELECTED,	DEP_STATIC });
			keyword_map.put( "file_exts_selected",		new int[]{KW_FILE_EXTS_SELECTED,	DEP_STATIC });
			keyword_map.put( "savepath", 				new int[]{KW_SAVE_PATH,				DEP_STATIC });
			keyword_map.put( "save_path", 				new int[]{KW_SAVE_PATH,				DEP_STATIC });
			keyword_map.put( "savefolder", 				new int[]{KW_SAVE_FOLDER,			DEP_STATIC });
			keyword_map.put( "save_folder", 			new int[]{KW_SAVE_FOLDER,			DEP_STATIC });
			
			keyword_map.put( "maxup", 					new int[]{KW_MAX_UP,				DEP_RUNNING });
			keyword_map.put( "max_up", 					new int[]{KW_MAX_UP,				DEP_RUNNING });
			keyword_map.put( "maxdown", 				new int[]{KW_MAX_DOWN,				DEP_RUNNING });
			keyword_map.put( "max_down", 				new int[]{KW_MAX_DOWN,				DEP_RUNNING });
			
			keyword_map.put( "torrent_type",			new int[]{KW_TORRENT_TYPE,			DEP_STATIC });
			keyword_map.put( "torrenttype", 			new int[]{KW_TORRENT_TYPE,			DEP_STATIC });
			
			keyword_map.put( "filepaths", 				new int[]{KW_FILE_PATHS,			DEP_STATIC });
			keyword_map.put( "file_paths", 				new int[]{KW_FILE_PATHS,			DEP_STATIC });
			keyword_map.put( "filepathsselected",		new int[]{KW_FILE_PATHS_SELECTED,	DEP_STATIC });
			keyword_map.put( "file_paths_selected",		new int[]{KW_FILE_PATHS_SELECTED,	DEP_STATIC });
			
			keyword_map.put( "targetratio", 			new int[]{KW_TARGET_RATIO,			DEP_TIME });	// time because can change with config change
			keyword_map.put( "target_ratio", 			new int[]{KW_TARGET_RATIO,			DEP_TIME });
			keyword_map.put( "tagnames",				new int[]{KW_TAG_NAMES,				DEP_STATIC });
			keyword_map.put( "tag_names",				new int[]{KW_TAG_NAMES,				DEP_STATIC });
			keyword_map.put( "tracker_status",			new int[]{KW_TRACKER_STATUS,		DEP_STATIC });
			keyword_map.put( "trackerstatus",			new int[]{KW_TRACKER_STATUS,		DEP_STATIC });
			keyword_map.put( "fullcopyseen",			new int[]{KW_FULL_COPY_SEEN,		DEP_RUNNING });
			keyword_map.put( "full_copy_seen",			new int[]{KW_FULL_COPY_SEEN,		DEP_RUNNING });

			keyword_map.put( "remaining", 				new int[]{KW_REMAINING,				DEP_RUNNING });
			
			keyword_map.put( "down_speed", 				new int[]{KW_DOWN_SPEED,			DEP_RUNNING });
			keyword_map.put( "downspeed", 				new int[]{KW_DOWN_SPEED,			DEP_RUNNING });
			keyword_map.put( "up_speed", 				new int[]{KW_UP_SPEED,				DEP_RUNNING });
			keyword_map.put( "upspeed", 				new int[]{KW_UP_SPEED,				DEP_RUNNING });
			keyword_map.put( "session_age", 			new int[]{KW_SESSION_AGE,			DEP_RUNNING });
			keyword_map.put( "sessionage", 				new int[]{KW_SESSION_AGE,			DEP_RUNNING });
			
			keyword_map.put( "my_rating",	 			new int[]{KW_PLUGIN_MY_RATING,		DEP_TIME });
			keyword_map.put( "myrating",	 			new int[]{KW_PLUGIN_MY_RATING,		DEP_TIME });
			
			keyword_map.put( "max32",	 				new int[]{KW_MAX32,					DEP_STATIC });
			keyword_map.put( "min32",	 				new int[]{KW_MIN32,					DEP_STATIC });
			keyword_map.put( "max64",	 				new int[]{KW_MAX64,					DEP_STATIC });
			keyword_map.put( "min64",	 				new int[]{KW_MIN64,					DEP_STATIC });
			
			keyword_map.put( "mocpath",	 				new int[]{KW_MOC_PATH,				DEP_STATIC });
			keyword_map.put( "moc_path",	 			new int[]{KW_MOC_PATH,				DEP_STATIC });
			keyword_map.put( "move_on_complete_path",	new int[]{KW_MOC_PATH,				DEP_STATIC });
			
			keyword_map.put( "filecountselected", 		new int[]{KW_FILE_COUNT_SELECTED,	DEP_STATIC });
			keyword_map.put( "file_count_selected",		new int[]{KW_FILE_COUNT_SELECTED,	DEP_STATIC });

			keyword_map.put( "trackers", 				new int[]{KW_TRACKERS,				DEP_STATIC });

		}

		private class
		ConstraintExprFunction
			implements  ConstraintExpr
		{
			private	final String 				func_name;
			private final ConstraintExprParams	params_expr;
			private final Object[]				params;

			private final int	fn_type;

			private Map<String, Object[]>	matches_cache = new HashMap<>();
						
			private
			ConstraintExprFunction(
				String 					_func_name,
				ConstraintExprParams	_params )
			{
				func_name	= _func_name;
				params_expr	= _params;

				params		= _params.getValues();

				Integer _fn_type = fn_map.get( func_name.toLowerCase( Locale.US ));
				
				if ( _fn_type == null ){
					
					throw( new RuntimeException( "Unsupported function '" + func_name + "'" ));
				}
				
				fn_type = _fn_type;
				
				int num_params = params.length;
				
				boolean	params_ok = false;

				switch( fn_type ){
				
					case FT_HAS_TAG:
					case FT_HAS_TAG_AGE:
					case FT_COUNT_TAG:
					case FT_TAG_POSITION:{
					
						params_ok = num_params >= 1 && getStringLiteral( params, 0 );
	
						if ( params_ok ){
							
							if ( fn_type == FT_TAG_POSITION ){
								
								if ( num_params > 1 ){
									
									params_ok = num_params == 2 && getNumericLiteral( params, 1 );
								}
							}else{
								
								params_ok = num_params == 1;
							}
						}
							
						if ( params_ok ){
							
							String tag_name = (String)params[0];
							
							if ( handler.tag_manager != null ){
								
								List<Tag> tags = handler.tag_manager.getTagsByName( tag_name, true );
								
								if ( tags.isEmpty()){
									
									throw( new RuntimeException( "Tag '" + tag_name + "' not found" ));
								}
								
								for ( Tag t: tags ){
									
									TagType tt = t.getTagType();
									
									if ( 	tt.hasTagTypeFeature( TagFeature.TF_PROPERTIES ) ||
											tt.getTagType() == TagType.TT_PEER_IPSET ){
										
										if ( dependent_on_tags == null ){
									
											dependent_on_tags = new HashSet<Tag>();
										}
									
										if ( tt.getTagType() == TagType.TT_PEER_IPSET ){
										
											dependent_on_peer_sets = true;
										}
										
										dependent_on_tags.add( t );
									}
								}
							}
						}
						
						break;
					}
					case FT_GET_TAG_WEIGHT:{
																
						params_ok = num_params <= 2;
							
						for( int i=0;i<num_params&&params_ok;i++){
						
							params_ok = getStringLiteral( params, i );
						}
							
						if ( params_ok ){
							
								// no params -> all tag weights, 1 param - all weights + options, s param - first comma sep tag list, second opts
							
							if ( num_params > 0 ){
								
								String 	options;
								String	tags_str;
								
								if ( num_params == 1 ){
								
									tags_str	= "";
									options 	= (String)params[0];
									
								}else{
									
									tags_str	= (String)params[0];
									options 	= (String)params[1];
								}
							
								if ( handler.tag_manager != null && !tags_str.isEmpty()){
								
									String[] tag_names = tags_str.split(",");
									
									for ( String tag_name: tag_names ){
										
										tag_name = tag_name.trim();
										
										if ( tag_name.isEmpty()){
											
											continue;
										}
								
										List<Tag> tags = handler.tag_manager.getTagsByName( tag_name, true );
								
										if ( tags.isEmpty()){
									
											throw( new RuntimeException( "Tag '" + tag_name + "' not found" ));
										}
																										
										if ( tag_weights == null ){
												
											tag_weights = new HashSet<Tag>();
										}
											
										tag_weights.addAll( tags );
									}
								}
								
								options = options.trim();
								
								if ( !options.isEmpty()){
									
									String[] bits = options.split( "=" );
									
									if ( bits.length != 2 || !bits[0].toLowerCase(Locale.US).equals("type")){
										
										throw( new RuntimeException( "options '" + options + "' invalid" ));
									}
									
									String rhs = bits[1].toLowerCase(Locale.US);
									
									if ( rhs.equals( "max" )){
										tag_weights_opt = 0;
									}else if ( rhs.equals( "min" )){
										tag_weights_opt = 1;
									}else if ( rhs.equals( "cumulative" )){
										tag_weights_opt = 2;
									}else{
										throw( new RuntimeException( "options '" + options + "' invalid" ));
									}
								}
							}
						}
						
						break;
					}
					case FT_HAS_NET:{

						params_ok = num_params == 1 && getStringLiteral( params, 0 );
	
						if ( params_ok ){
	
							params[0] = AENetworkClassifier.internalise((String)params[0]);
	
							params_ok = params[0] != null;
						}
						
						break;
					}
					case FT_IS_PRIVATE:
					case FT_IS_SHARE:
					case FT_IS_MAGNET:
					case FT_IS_LOW_NOISE:
					case FT_IS_NEW:
					case FT_CAN_ARCHIVE:
					case FT_IS_SEQUENTIAL:
					case FT_IS_IP_FILTERED:{

						params_ok = num_params == 0;
						
						break;
					}

					case FT_IS_FORCE_START:
					case FT_IS_SUPER_SEEDING:
					case FT_IS_CHECKING:
					case FT_IS_MOVING:
					case FT_IS_COMPLETE:
					case FT_IS_STOPPED:
					case FT_IS_SEEDING:
					case FT_IS_DOWNLOADING:
					case FT_IS_RUNNING:
					case FT_IS_ERROR:
					case FT_IS_PAUSED:
					case FT_IS_UNALLOCATED:
					case FT_IS_QUEUED:{
					
						depends_on_download_state = true;

						params_ok = num_params == 0;
						
						break;
					}
					case FT_GE:
					case FT_GT:
					case FT_LE:
					case FT_LT:
					case FT_EQ:
					case FT_NEQ:
					case FT_PLUS:
					case FT_MINUS:
					case FT_MULT:
					case FT_DIV:
					case FT_REM:
					case FT_MIN:
					case FT_MAX:{

						params_ok = num_params == 2;

						break;
					}
					case FT_LENGTH:
					case FT_COUNT:{
						params_ok = num_params == 1;
						
						break;
					}
					case FT_CONTAINS:{

						params_ok = num_params == 2 || (  num_params == 3 && getNumericLiteral( params, 2 ));
						
						break;
					}
					case FT_LOWERCASE:{
				
						params_ok = num_params == 1;
						
						break;
					}
					case FT_MATCHES:{

						params_ok = ( num_params == 2 || num_params == 3) && getStringLiteral( params, 1 );
	
						if ( params_ok && num_params == 3 ){
							
							params_ok = getNumericLiteral( params, 2 );
						}
						
						if ( params_ok ){
							
							try{
								boolean	case_insensitive = true;
								
								if ( num_params == 3 ){
																		
									Number flags = (Number)params[2];
										
									if ( flags.intValue() == 0 ){
											
										case_insensitive = false;
									}
								}
								
								if ( case_insensitive ){
									
									Pattern.compile((String)params[1], Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );
									
								}else{
									
									Pattern.compile((String)params[1] );
								}
							}catch( Throwable e ) {
								
								setError( "Invalid constraint pattern: " + params[1] + ": " + e.getMessage());
							}
						}
					
						break;
					}
					case FT_JAVASCRIPT:{
				
						params_ok = num_params == 1 && getStringLiteral( params, 0 );

						depends_on_download_state = true;	// dunno so let's assume so
						
						break;
					}
					case FT_HAS_TAG_GROUP:{
				
						params_ok = num_params == 1 && getStringLiteral( params, 0 );
						
						break;
					}
					case FT_HOURS_TO_SECS:
					case FT_DAYS_TO_SECS:
					case FT_WEEKS_TO_SECS:{
				

						params_ok = num_params == 1 && getNumericLiteral( params, 0 );
						
						break;
					}
					case FT_TIME_TO_ELAPSED:
					case FT_TO_MB:
					case FT_TO_MiB:
					case FT_TO_GB:
					case FT_TO_GiB:{

						params_ok = num_params == 1;
						
						break;
					}
					case FT_GET_CONFIG:{

						params_ok = num_params == 1 && getStringLiteral( params, 0 );
						
						if ( params_ok ){
							
							String key = (String)params[0];
							
							key = key.toLowerCase( Locale.US );
							
							params[0] = key;
									
							if ( !config_key_map.containsKey( key )){
								
								throw( new RuntimeException( "Unsupported configuration parameter: " + key ));
							}
						}
						
						break;
					}
					case FT_SET_COLOURS:{

						params_ok = num_params >= 1&&  num_params <= 3;
	
						if ( params_ok ){
							
							for ( int i=0;i<num_params;i++){
							
								params_ok = getNumericLiteral( params, i );
								
								if ( !params_ok ){
									
									break;
								}
							}
						}
						
						break;
					}
					case FT_SET_TAG_SORT:{

						params_ok = num_params >= 1 && num_params <= 2;
						
						String option = null;
						
						if ( params_ok ){
							
								// we allow first param to be a string in the case of "random"
							
							if ( num_params == 1 ){
								
								if ( getStringLiteral( params, 0 )){
									
									option = (String)params[0];
								}
							}
							
							if ( num_params == 2 ){
								
								params_ok = getStringLiteral( params, 1 );
								
								if ( params_ok ){
									
									option = (String)params[1];
								}
							}
						}
						
						if ( params_ok && option != null ){
						
							if ( option.equals( "r" ) || option.equals( "reverse") || option.equals( "random" )){
								
							}else{
								
								throw( new RuntimeException( "option '" + option + "' invalid" ));
							}
						}
						
						break;
					}
					case FT_GET_TAG_SORT:{
						
						params_ok = num_params <= 2;
						
						for( int i=0;i<num_params&&params_ok;i++){
						
							params_ok = getStringLiteral( params, i );
						}
							
						if ( params_ok ){
							
								// no params -> current tag sort, 1 param - tag list comma sep, 2 param - tag list comma sep + options
							
							if ( num_params > 0 ){
								
								String	tags_str;
								String 	options;

								if ( num_params == 1 ){
								
									tags_str 	= (String)params[0];
									options	= "";

								}else{
									
									tags_str	= (String)params[0];
									options 	= (String)params[1];
								}
							
								if ( handler.tag_manager != null && !tags_str.isEmpty()){
								
									String[] tag_names = tags_str.split(",");
									
									for ( String tag_name: tag_names ){
										
										tag_name = tag_name.trim();
										
										if ( tag_name.isEmpty()){
											
											continue;
										}
								
										List<Tag> tags = handler.tag_manager.getTagsByName( tag_name, true );
								
										if ( tags.isEmpty()){
									
											throw( new RuntimeException( "Tag '" + tag_name + "' not found" ));
										}
																										
										if ( tag_sorts == null ){
												
											tag_sorts = new HashSet<Tag>();
										}
											
										tag_sorts.addAll( tags );
									}
								}
								
								options = options.trim();
								
								if ( !options.isEmpty()){
									
									String[] bits = options.split( "=" );
									
									if ( bits.length != 2 || !bits[0].toLowerCase(Locale.US).equals("type")){
										
										throw( new RuntimeException( "options '" + options + "' invalid" ));
									}
									
									String rhs = bits[1].toLowerCase(Locale.US);
									
									if ( rhs.equals( "max" )){
										tag_sorts_opt = 0;
									}else if ( rhs.equals( "min" )){
										tag_sorts_opt = 1;
									}else if ( rhs.equals( "cumulative" )){
										tag_sorts_opt = 2;
									}else{
										throw( new RuntimeException( "options '" + options + "' invalid" ));
									}
								}
							}
						}
						
						break;
					}
					case FT_COUNT_TRACKERS:{

						params_ok = num_params == 0;
						
						break;
					}
					case FT_IF_THEN_ELSE:{
										
						params_ok = num_params == 3;
						
						break;
					}
					case FT_TRACKER_PEERS:
					case FT_TRACKER_SEEDS:{
						
						params_ok = num_params == 1 && getStringLiteral( params, 0 );
						
						break;
					}
					case FT_PLUGIN_OPTION:{
						
						params_ok = num_params == 2 && getStringLiteral( params, 0 ) && getStringLiteral( params, 1 );
						
						break;
					}
					default:{

						throw( new RuntimeException( "Unsupported function '" + fn_type + "'" ));
					}
				}
				
				if ( !params_ok ){

					throw( new RuntimeException( "Invalid parameters for function '" + func_name + "': " + params_expr.getString()));

				}
			}

			@Override
			public Object
			eval(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				StringBuilder		debug )
			{
				if ( debug!=null){
					debug.append( "[" + func_name + "(" );
				}
				
				Object res = evalSupport( context, dm, tags, debug );
				
				if ( debug!=null){
					debug.append( ")->" + res + "]" );
				}
				
				return( res );
			}
			
			public Object
			evalSupport(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				StringBuilder		debug )
			{
				int num_params = params.length;

				switch( fn_type ){
					case FT_HAS_TAG:{

						String tag_name = getStringParam( params, 0, debug );

						for ( Tag t: tags ){

							if ( t.getTagName( true ).equals( tag_name )){

								return( true );
							}
						}

						if ( dependent_on_peer_sets ){
						
							Set<Tag>	ps_tags = (Set<Tag>)dm.getUserData( DM_PEER_SETS );
							
							if ( ps_tags != null ){
								
								for ( Tag t: ps_tags ){

									if ( t.getTagName( true ).equals( tag_name )){

										return( true );
									}
								}
							}
						}
						
						return( false );
					}
					case FT_HAS_TAG_AGE:{

						String tag_name = getStringParam( params, 0, debug );

						Tag target = null;
						
						if ( dependent_on_tags != null ){
							
							for ( Tag t: dependent_on_tags ){
	
								if ( t.getTagName( true ).equals( tag_name )){
	
									target = t;
									
									break;
								}
							}
						}
						
						if ( target == null ){
							
							setError( "Tag '" + tag_name + "' not found" );
							
							return( 0 );
						}				
						
						if ( !target.hasTaggable( dm )){
							
							return( -1 );
						}
						
						long tag_added = target.getTaggableAddedTime( dm );

						if ( tag_added <= 0 ){

							return( 0 );
						}

						long age = (( SystemTime.getCurrentTime() - tag_added )/1000 );		// secs

						if ( age < 0 ){

							age = 0;
						}
						
						return( age );
					}
					case FT_HAS_TAG_GROUP:{

						String group_name = getStringParam( params, 0, debug );

						for ( Tag t: tags ){

							String group = t.getGroup();
							
							if ( group != null && group.equals( group_name )){

								return( true );
							}
						}

						return( false );
					}
					case FT_COUNT_TAG:{
						
						String tag_name = getStringParam( params, 0, debug );
						
						Tag target = null;
						
						if ( dependent_on_tags != null ){
							
							for ( Tag t: dependent_on_tags ){
	
								if ( t.getTagName( true ).equals( tag_name )){
	
									target = t;
									
									break;
								}
							}
						}	
						
						if ( target == null ){
							
							setError( "Tag '" + tag_name + "' not found" );
							
							return( 0 );
							
						}else{
							
							return( target.getTaggedCount());
						}
					}
					case FT_TAG_POSITION:{
						
						String tag_name = getStringParam( params, 0, debug );
						
						Tag target = null;
						
						if ( dependent_on_tags != null ){
							
							for ( Tag t: dependent_on_tags ){
	
								if ( t.getTagName( true ).equals( tag_name )){
	
									target = t;
									
									break;
								}
							}
						}	
						
						if ( target == null ){
							
							setError( "Tag '" + tag_name + "' not found" );
							
							return( -1 );
							
						}else{
							
							int sort;
							
							if ( params.length == 1 ){
							
								sort = 1;
								
							}else{
								
								sort = getNumericParam( params, 1, debug ).intValue();
							}
							
							Set<Taggable> dms = target.getTagged();
							
							if ( !dms.contains( dm )){
								
								return( -1 );	// not present
							}
							
							if ( sort == 1 ){
								
								int my_position = dm.getPosition();
								
								int tag_position = 0;
								
								boolean found = false;
								
								for ( Taggable t: dms ){
									
									DownloadManager this_dm = (DownloadManager)t;
									
									if ( t == dm ){
										
										found = true;
										
									}else{
										
										int this_pos = this_dm.getPosition();
										
										if ( this_pos < my_position ){
											
											tag_position++;
										}
									}
								}
								
								if ( found ){
									
									return( tag_position );
									
								}else{
									
									return( -1 );
								}
							}else{
								
								long my_time = target.getTaggableAddedTime( dm );
								
								if ( my_time == -1 ){
									
									return( -1 );	// not in tag
								}
								
									// need to mix in position as if a number of downloads are added to a tag at the same time (drag+drop a bunch)
									// then they most likely end up with identical times
								
								my_time += dm.getPosition();
								
								int tag_position = 0;
								
								boolean found = false;
								
								for ( Taggable t: dms ){
																		
									if ( t == dm ){
											
										found = true;
									
									}else{
										
										long this_time = target.getTaggableAddedTime( t );
										
										if ( this_time != -1 ){
											
											DownloadManager this_dm = (DownloadManager)t;
											
											this_time += this_dm.getPosition();
											
											if ( this_time < my_time ){
												
												tag_position++;
											}
										}
									}
								}
								
								if ( found ){
									
									return( tag_position );
									
								}else{
									
									return( -1 );
								}
							}
						}
					}
					case FT_GET_TAG_WEIGHT:{
						
						int result = 0;
						
						for ( Tag tag: tags ){
							
							if ( tag_weights != null ){
								
								if ( !tag_weights.contains( tag )){
									
									continue;
								}
							}
							
							TagDownload tag_dl = (TagDownload)tag;
							
							int w = tag_dl.getWeight();
							
							if ( w > 0 ){
								
								if ( tag_weights_opt == 0 ){
									result = Math.max( result, w );
								}else if ( tag_weights_opt == 1 ){
									result = Math.min( result, w );
								}else{
									result += w;
								}
							}
						}
						
						return( result );
					}
					case FT_HAS_NET:{

						String net_name = getStringParam( params, 0, debug );

						if ( net_name != null ){

							String[] nets = dm.getDownloadState().getNetworks();

							if ( nets != null ){

								for ( String net: nets ){

									if ( net == net_name ){

										return( true );
									}
								}
							}
						}

						return( false );
					}
					case FT_IS_PRIVATE:{

						TOTorrent t = dm.getTorrent();

						return( t != null && t.getPrivate());
					}
					case FT_IS_SHARE:{

						ShareManager sm = handler.share_manager;
						
						if ( sm == null ){
							
							return( false );
						}
							
						try{
							return( sm.lookupShare( dm.getTorrent().getHash()) != null );
							
						}catch( Throwable e ){
							
							return( false );
						}
					}
					case FT_IS_FORCE_START:{

						return( dm.isForceStart());
					}
					case FT_IS_CHECKING:{

						int state = dm.getState();

						if ( state == DownloadManager.STATE_CHECKING ){

							return( true );

						}else if ( state == DownloadManager.STATE_SEEDING ){

							DiskManager disk_manager = dm.getDiskManager();

							if ( disk_manager != null ){

								return( disk_manager.getCompleteRecheckStatus() != -1 );
							}
						}

						return( false );
					}
					case FT_IS_MOVING:{

						return( dm.getMoveProgress() != null || FileUtil.hasTask( dm ));
					}
					case FT_IS_SUPER_SEEDING:{
						
						PEPeerManager pm = dm.getPeerManager();
						
						if ( pm != null ){
							
							return( pm.isSuperSeedMode());
						}
						
						return( false );
					}
					case FT_IS_COMPLETE:{

						return( dm.isDownloadComplete( false ));
					}
					case FT_IS_STOPPED:{

						int state = dm.getState();

						return( state == DownloadManager.STATE_STOPPED && !dm.isPaused());
					}
					case FT_IS_SEEDING:{

						int state = dm.getState();

						return( state == DownloadManager.STATE_SEEDING );
					}
					case FT_IS_DOWNLOADING:{

						int state = dm.getState();

						return( state == DownloadManager.STATE_DOWNLOADING );
					}
					case FT_IS_RUNNING:{

						int state = dm.getState();

						return( state != DownloadManager.STATE_STOPPED &&
								state != DownloadManager.STATE_ERROR &&
								state != DownloadManager.STATE_QUEUED );
								
					}
					case FT_IS_ERROR:{

						int state = dm.getState();

						return( state == DownloadManager.STATE_ERROR );
					}
					case FT_IS_MAGNET:{

						return( dm.getDownloadState().getFlag(DownloadManagerState.FLAG_METADATA_DOWNLOAD ));
					}
					case FT_IS_LOW_NOISE:{

						return( dm.getDownloadState().getFlag(DownloadManagerState.FLAG_LOW_NOISE ));
					}
					case FT_IS_NEW:{
						
						return(  dm.getAssumedComplete() && !PlatformTorrentUtils.getHasBeenOpened(dm));
					}
					case FT_IS_UNALLOCATED:{
						
						return( !dm.isDataAlreadyAllocated());
					}
					case FT_IS_PAUSED:{

						return( dm.isPaused());
					}
					case FT_IS_QUEUED:{

						int state = dm.getState();

						return( state == DownloadManager.STATE_QUEUED );
					}
					case FT_CAN_ARCHIVE:{

						Download dl = PluginCoreUtils.wrap( dm );

						return( dl != null && dl.canStubbify());
					}
					case FT_GE:
					case FT_GT:
					case FT_LE:
					case FT_LT:
					case FT_EQ:
					case FT_NEQ:{

						Number n1 = getNumeric( context, dm, tags, params, 0, debug );
						Number n2 = getNumeric( context, dm, tags, params, 1, debug );

						switch( fn_type ){

							case FT_GE:
								return( n1.doubleValue() >= n2.doubleValue());
							case FT_GT:
								return( n1.doubleValue() > n2.doubleValue());
							case FT_LE:
								return( n1.doubleValue() <= n2.doubleValue());
							case FT_LT:
								return( n1.doubleValue() < n2.doubleValue());
							case FT_EQ:
								return( n1.doubleValue() == n2.doubleValue());
							case FT_NEQ:
								return( n1.doubleValue() != n2.doubleValue());
						}

						return( false );
					}
					case FT_LENGTH:{
						
						String		str = getString( context, dm, tags, params, 0, debug );
						
						if ( str == null ){
							
							return( -1 );
							
						}else{
							
							return( str.length());
						}
					}
					case FT_COUNT:{
						
						String[]		strs = getStrings( context, dm, tags, params, 0, debug );
						
						return( strs.length );
					}
					case FT_CONTAINS:{

						String[]	s1s = getStrings( context, dm, tags, params, 0, debug );
						
						String		s2 = getString( context, dm, tags, params, 1, debug );
						
						boolean	case_insensitive = false;
						
						if ( num_params == 3 && getNumericLiteral( params, 2 )){
							
							Number flags = getNumericParam( params, 2, debug );
							
							if ( flags.intValue() == 1 ){
								
								case_insensitive = true;
							}
						}
												
						if ( s2.contains( "|" )){
							
							String pat_str = "";
							String[] bits = s2.split( "\\|");
							boolean hasSpace = s2.contains( " " );
							if (hasSpace) {
								s2 = "";
							}
							for ( String bit: bits ){
								bit = bit.trim();
								if ( !bit.isEmpty()){
									if (hasSpace) {
										s2 += (s2.isEmpty()?"":"|") + bit;
									}
									pat_str += (pat_str.isEmpty()?"":"|") + Pattern.quote(bit);
								}
							}
							if (hasSpace) {
								params[1] = "\"" + s2 + "\"";
							}
							
							Pattern pattern = RegExUtil.getCachedPattern( "tag:constraint:" + tag_maybe_null.getTagUID(), pat_str, case_insensitive?0:(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));

							String key = dm.getInternalName();
							
							if ( !key.isEmpty()){
								
								Object[] cache = matches_cache.get( key );
								
								if ( cache != null ){
									
									if ( cache[1] == pattern ){
										
										String[] old_s1s = (String[])cache[0];
										
										if ( s1s == old_s1s || Arrays.equals( old_s1s, s1s )){
										
											return((Boolean)cache[2]);
										}
									}
								}
							}
							
							boolean result = false;
							
							for ( String s1: s1s ){
							
								if ( pattern.matcher( s1 ).find()){
									
									result = true;
									
									break;
								}
							}
							
							matches_cache.put( key, new Object[]{ s1s, pattern, result });

							return( result );
							
						}else{
							
							if ( case_insensitive ){
								
								s2 = s2.toLowerCase( Locale.US );
								
								for ( String s1: s1s ){
									
									if ( s1.toLowerCase( Locale.US).contains( s2 )){
										
										return( true );
									}
								}
							}else{						
								for ( String s1: s1s ){
								
									if ( s1.contains( s2 )){
										
										return( true );
									}
								}
							}
						}
						
						return( false );
					}
					case FT_LOWERCASE:{
						
						String	s = getString( context, dm, tags, params, 0, debug );

						return( s.toLowerCase( Locale.US ));
					}
					case FT_MATCHES:{

						String[]	s1s = getStrings( context, dm, tags, params, 0, debug );

						if ( params[1] == null ){

							return( false );

						}else{
							
							Pattern pattern;
							
							if ( params[1] instanceof Pattern ){
						
								pattern = (Pattern)params[1];
								
							}else{
	
								boolean	case_insensitive = true;
								
								if ( num_params == 3 ){
																		
									Number flags = (Number)params[2];
										
									if ( flags.intValue() == 0 ){
											
										case_insensitive = false;
									}
								}
								
								try{
									if ( case_insensitive ){
									
										pattern = Pattern.compile((String)params[1], Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );
										
									}else{
										
										pattern = Pattern.compile((String)params[1] );
									}
									
									params[1] = pattern;							

								}catch( Throwable e ){

									setError( "Invalid constraint pattern: " + params[1] + ": " + e.getMessage());
								
									params[1] = null;
									
									return( false );
								}
							}
							
							if ( debug != null ){
								debug.append( ",\"" + pattern + "\"");
							}
							
							String key = dm.getInternalName();
							
							if ( !key.isEmpty()){
								
								Object[] cache = matches_cache.get( key );
								
								if ( cache != null ){
									
									if ( cache[1] == pattern ){
										
										String[] old_s1s = (String[])cache[0];
										
										if ( s1s == old_s1s || Arrays.equals( old_s1s, s1s )){
										
											return((Boolean)cache[2]);
										}
									}
								}
							}
							
							boolean result = false;
							
							for ( String s1: s1s ){
							
								if ( pattern.matcher( s1 ).find()){
									
									result = true;
									
									break;
								}
							}
							
							matches_cache.put( key, new Object[]{ s1s, pattern, result });
							
							return( result );
						}
					}
					case FT_JAVASCRIPT:{

						Object result =
							handler.tag_manager.evalScript(
								tag_maybe_null,
								"javascript( " + (String)params[0] + ")",
								Arrays.asList( dm ),
								"inTag" );

						if ( result instanceof Boolean ){

							return((Boolean)result);
							
						}else if ( result instanceof Throwable ){
							
							setError( Debug.getNestedExceptionMessage((Throwable)result ));
						}

						return( false );
					}
					case FT_HOURS_TO_SECS:{

						Number n1 = getNumeric( context, dm, tags, params, 0, debug );
						
						return((long)( n1.doubleValue() * 60*60 ));
					}
					case FT_DAYS_TO_SECS:{

						Number n1 = getNumeric( context, dm, tags, params, 0, debug  );
						
						return((long)( n1.doubleValue() * 24*60*60 ));
					}
					case FT_WEEKS_TO_SECS:{

						Number n1 = getNumeric( context, dm, tags, params, 0, debug  );
						
						return((long)( n1.doubleValue() * 7*24*60*60 ));
					}
					case FT_TIME_TO_ELAPSED:{
						
						long time = getNumeric( context, dm, tags, params, 0, debug  ).longValue();
						
						if ( time > 0 ){
							
							long secs = (SystemTime.getCurrentTime() - time )/1000;
							
							if ( Math.abs( secs ) < 5 ){
								
								secs = 0;	// keep things sensible when there's a slight drift during evaluation
							}
							
							return( secs );
							
						}else{
							
							return( time );
						}
					}
					case FT_TO_MB:{
						
						long bytes = getNumeric( context, dm, tags, params, 0, debug  ).longValue();
						
						long multiplier = 1000;
						
						return( bytes/(multiplier*multiplier));
					}
					case FT_TO_MiB:{
						
						long bytes = getNumeric( context, dm, tags, params, 0, debug  ).longValue();
						
						long multiplier = 1024;
						
						return( bytes/(multiplier*multiplier));
					}
					case FT_TO_GB:{
						
						long bytes = getNumeric( context, dm, tags, params, 0, debug  ).longValue();
						
						long multiplier = 1000;
						
						return( bytes/(multiplier*multiplier*multiplier));
					}
					case FT_TO_GiB:{
						
						long bytes = getNumeric( context, dm, tags, params, 0, debug  ).longValue();

						long multiplier = 1024;
						
						return( bytes/(multiplier*multiplier*multiplier));
					}
					case FT_PLUS:
					case FT_MINUS:
					case FT_MULT:
					case FT_DIV:
					case FT_REM:
					case FT_MIN:
					case FT_MAX:{
						
						Number n1 = getNumeric( context, dm, tags, params, 0, debug  );
						Number n2 = getNumeric( context, dm, tags, params, 1, debug  );
						
						if ( 	n1 instanceof Double ||
								n1 instanceof Float ||
								n2 instanceof Double ||
								n2 instanceof Float ){
							
							double p1 = n1.doubleValue();
							double p2 = n2.doubleValue();
							
							switch( fn_type ){
								case FT_PLUS:	return( p1+p2 );
								case FT_MINUS:	return( p1-p2 );
								case FT_MULT:	return( p1*p2 );
								case FT_DIV:	return( p1/p2 );
								case FT_REM:	return( p1%p2 );
								case FT_MIN:	return( Math.min(p1,p2 ));
								case FT_MAX:	return( Math.max(p1,p2 ));
							}
						}else{
							
							long p1 = n1.longValue();
							long p2 = n2.longValue();
							
							switch( fn_type ){
								case FT_PLUS:	return( p1+p2 );
								case FT_MINUS:	return( p1-p2 );
								case FT_MULT:	return( p1*p2 );
								case FT_DIV:	return( p1/p2 );
								case FT_REM:	return( p1%p2 );
								case FT_MIN:	return( Math.min(p1,p2 ));
								case FT_MAX:	return( Math.max(p1,p2 ));
							}
						}
					}
					case FT_GET_CONFIG:{
						
						String key = getStringParam( params, 0, debug );
						
						long now = SystemTime.getMonotonousTime();
						
						Object[] existing = config_value_cache.get( key );
						
						if ( existing != null ){
							
							if ( now - ((Long)existing[0]) < 60*1000 ){
								
								return( existing[1]);
							}
						}
						
						String[] entry = config_key_map.get( key );
						
						if ( entry[0] == CONFIG_FLOAT ){
							
							Object result = COConfigurationManager.getFloatParameter(entry[1]);
							
							config_value_cache.put( key, new Object[]{ now, result });
							
							return( result );
						}
						
						setError( "Error getting config value for '" + key + "'" );
						
						return( 0 );
					}
					case FT_SET_COLOURS:{
						
						long[] p = new long[ num_params ];
						
						for ( int i=0;i<num_params;i++){
							
							p[i] = getNumeric( context, dm, tags, params, i, debug  ).longValue();
						}
						context.put( EVAL_CTX_COLOURS, p);
						
						return( true );
					}
					case FT_SET_TAG_SORT:{
						
						Object[] p;
						
						if ( num_params == 1 && params[0] instanceof String && ((String)params[0]).equals( "random" )){
							
							p = new Object[ 2 ];
							 
							p[0] = tag_maybe_null==null?0:RandomUtils.nextInt( Math.max( 1, tag_maybe_null.getTaggedCount()));
							
							p[1] = (String)params[0];
							
						}else{
							
							p  = new Object[ num_params ];
							
							p[0] = getNumeric( context, dm, tags, params, 0, debug  ).longValue();
							
							if ( p.length > 1 ){
								
								p[1] = (String)params[1];
							}
						}
						
						context.put( EVAL_CTX_TAG_SORT, p);
						
						return( true );
					}
				case FT_GET_TAG_SORT:{
						
						long result = 0;
						
						Map<Long,Object[]> tag_sort = (Map<Long,Object[]>)dm.getDownloadState().getTransientAttribute( DownloadManagerState.AT_TRANSIENT_TAG_SORT );
						
						if ( tag_sort != null ){
							
							for ( Tag tag: tags ){
								
								if ( tag_sorts != null ){
									
									if ( !tag_sorts.contains( tag )){
										
										continue;
									}
								}
								
								Object[] entry = tag_sort.get( tag.getTagUID());
								
								if ( entry != null ){
								
									Long value = (Long)entry[1];

									if ( value != null ){
										
										if ( tag_sorts_opt == 0 ){
											result = Math.max( result, value );
										}else if ( tag_sorts_opt == 1 ){
											result = Math.min( result, value );
										}else{
											result += value;
										}
									}
								}
							}
						}
						
						return( result );
					}
					case FT_IS_SEQUENTIAL:{

						return( dm.getDownloadState().getFlag(DownloadManagerState.FLAG_SEQUENTIAL_DOWNLOAD ));
					}
					case FT_IS_IP_FILTERED:{
						
						return( ip_filter.isEnabled() && 
								!dm.getDownloadState().getFlag(DownloadManagerState.FLAG_DISABLE_IP_FILTER ));
					}
					case FT_COUNT_TRACKERS:{
						
						TOTorrent torrent = dm.getTorrent();
						
						int total = 0;

						if ( torrent != null ){
							
							TOTorrentAnnounceURLSet[] sets = torrent.getAnnounceURLGroup().getAnnounceURLSets();
														
							if ( sets.length == 0 ){
							
								URL url = torrent.getAnnounceURL();
								
								if ( !TorrentUtils.isDecentralised( url )){
									
									total++;
								}
							}else{
								
								for ( TOTorrentAnnounceURLSet set: sets ){
									
									total += set.getAnnounceURLs().length;
								}
							}
						}
						
						return( total );
					}
					case FT_IF_THEN_ELSE:{
						
						Number cond = getNumeric( context, dm, tags, params, 0, debug );
						
						if ( cond.intValue() != 0 ){
							
							return( getWhatever( context, dm, tags, params, 1, debug ));
							
						}else{
							
							return( getWhatever( context, dm, tags, params, 2, debug ));
						}
					}
					case FT_TRACKER_PEERS:
					case FT_TRACKER_SEEDS:{
						
						int state = dm.getState();
						
						if ( state != DownloadManager.STATE_DOWNLOADING && state != DownloadManager.STATE_SEEDING ){
							
							return( -1 );
						}
						
						String tracker = getStringParam( params, 0, debug ).toLowerCase( Locale.US );

						String target = null;
						
						String app_name = Constants.APP_NAME;
						
						if ( tracker.equals( app_name.toLowerCase( Locale.US ) + "dht") || tracker.equals( "dht" )){
							
							target = "dht";
							
						}else if ( tracker.equals( "mldht" )){
							
							target = "mldht";
									
						}else if ( tracker.equals( "i2pdht" )){
							
							target = "i2p";
							
						}else{
						
							setError( "Unsupported tracker type: " + tracker );
							
							return( -1 );
						}
						
						List<TrackerPeerSource> tps_list = dm.getTrackerPeerSources();

						for ( TrackerPeerSource tps: tps_list ){
							
							int type = tps.getType();
							
							if ( type == TrackerPeerSource.TP_DHT && target.equals( "dht" )){
								
								if ( fn_type == FT_TRACKER_PEERS ){
									
									return( tps.getLeecherCount());
									
								}else{
									
									return( tps.getSeedCount());
								}
							}else if ( type == TrackerPeerSource.TP_PLUGIN ){
																
								String details = tps.getDetails();
								
								if ( details == null ){
									
									continue;
								}
								
								if ( details.toLowerCase().startsWith( target )){
									
									if ( fn_type == FT_TRACKER_PEERS ){
									
										return( tps.getLeecherCount());
										
									}else{
										
										return( tps.getSeedCount());
									}
								}
							}			
						}

						return( -1 );
					}
					case FT_PLUGIN_OPTION:{
						
						String plugin_id	= getStringParam( params, 0, debug ).toLowerCase();
						
						if ( plugin_id.equalsIgnoreCase( "dht" )){

							plugin_id = "azbpdhdtracker";

						}else if ( plugin_id.equalsIgnoreCase( "I2P" )){

							plugin_id = "azneti2phelper";
						}

						String attr			= getStringParam( params, 1, debug ).toLowerCase();

						if ( !attr.equals( DownloadManagerState.AT_PO_ENABLE_ANNOUNCE )){
							
							setError( "Unsupported plugin option attribute type: " + attr );
							
							return( null );
						}
						
						boolean value = true;		// default
						
						Map all_opts = dm.getDownloadState().getMapAttribute( DownloadManagerState.AT_PLUGIN_OPTIONS );
						
						if ( all_opts != null ){
													
							Map opts = (Map)all_opts.get( plugin_id.toLowerCase( Locale.US ));
							
							if ( opts != null ){
								
								Number e = (Number)opts.get( DownloadManagerState.AT_PO_ENABLE_ANNOUNCE );
								
								if ( e != null ){
									
									value = e.intValue() != 0;
								}
							}
						}
						
						return( value );
					}
				}

				return( false );
			}

			private String
			getStringParam(
				Object[] 		params,
				int				index,
				StringBuilder	debug )
			{
				String result = (String)params[index];
				
				if ( debug != null ){
					if ( index > 0 ){
						debug.append(",");
					}
					debug.append( "\"" + result + "\"");
				}
				
				return( result );
			}
			
			private Number
			getNumericParam(
				Object[] 		params,
				int				index,
				StringBuilder	debug )
			{
				Number result = (Number)params[index];
				
				if ( debug != null ){
					if ( index > 0 ){
						debug.append(",");
					}
					debug.append( result );
				}
				
				return( result );
			}
			
			private Object
			getWhatever(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				Object[]			args,
				int					index,
				StringBuilder		debug )
			{
				if ( debug!=null&&index>0){
					debug.append( "," );
				}
				
				try{
					Object arg = args[index];
					
					if ( arg instanceof Number ){
						
						return( arg );
						
					}else if ( arg instanceof String ){
	
						String s_arg = (String)arg;
	
						if ( GeneralUtils.startsWithDoubleQuote( s_arg ) && GeneralUtils.endsWithDoubleQuote( s_arg )){
	
							return( s_arg.substring( 1, s_arg.length() - 1 ).replace("\\\"", "\""));
						}
						
						try{
							if ( s_arg.startsWith( "0x" )){
								
								args[index] = Long.parseLong( s_arg.substring( 2 ), 16 );
															
							}else if ( s_arg.startsWith( "#" )){
									
								args[index] = Long.parseLong( s_arg.substring( 1 ), 16 );
																	
							}else{
												
								args[index] =  Double.parseDouble( s_arg );
							}
								
							return( args[index] );
							
						}catch( Throwable e ){
							
						}
						
						Object result = getKeywordValue( dm, tags, s_arg );
						
						if ( result != null ){
							
							return( result );
						}
						
						result = getNumericSupport(dm, tags, args, index);
						
						if ( result != null ){
							
							return( result );
						}
						
						throw( new Exception( "Invalid constraint string: " + s_arg ));
						
					}else if ( arg instanceof ConstraintExpr ){		
						
						if ( debug!=null){
							debug.append( "[" );
						}
						
						Object res = ((ConstraintExpr)arg).eval( context, dm, tags, debug );
						
						if ( debug!=null){
							debug.append( "->" + res + "]" );
						}
						
						return( res );
						
					}else{
						
						throw( new Exception( "Invalid constraint string: " + arg ));
					}
				}catch( Throwable e ){
					
					setError( Debug.getNestedExceptionMessage( e ));
					
					String result = "\"\"";
		
					return( result );
				}
			}
			
			private boolean
			getStringLiteral(
				Object[]	args,
				int			index )
			{
				Object _arg = args[index];

				if ( _arg instanceof String ){

					String arg = (String)_arg;

					if ( GeneralUtils.startsWithDoubleQuote( arg ) && GeneralUtils.endsWithDoubleQuote( arg )){

						args[index] = arg.substring( 1, arg.length() - 1 ).replace("\\\"", "\"");

						return( true );
					}
				}

				return( false );
			}

			private boolean
			getNumericLiteral(
				Object[]	args,
				int			index )
			{
				Object arg = args[index];

				if ( arg instanceof Number ){

					return( true );
					
				}else if ( arg instanceof String ){
					
					String s_arg = (String)arg;
					
					try{
						if ( s_arg.startsWith( "0x" )){
							
							args[index] = Long.parseLong( s_arg.substring( 2 ), 16 );
							
							return( true );
							
						}else if ( s_arg.startsWith( "#" )){
								
							args[index] = Long.parseLong( s_arg.substring( 1 ), 16 );
								
							return( true );
								
						}else{
											
							args[index] =  Double.parseDouble( s_arg );
						
							return( true );
						}
								
					}catch( Throwable e ){
						
					}
				}

				return( false );
			}
			
			private String
			getString(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				Object[]			args,
				int					index,
				StringBuilder		debug )
			{
				String[] res = getStrings( context, dm, tags, args, index, debug );
				
				return( res[0] );
			}

			private String[]
			getStrings(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				Object[]			args,
				int					index,
				StringBuilder		debug )
			{
				if ( debug!=null&&index>0){
					debug.append( "," );
				}
				
				try{
					Object arg = args[index];
	
					if ( arg instanceof String ){
						
						String[] result;
						
						String str = (String)arg;
						
						if ( GeneralUtils.startsWithDoubleQuote( str ) && GeneralUtils.endsWithDoubleQuote( str )){

							result = new String[]{ str.substring( 1, str.length() - 1 ).replace("\\\"", "\"")};
							
							if ( debug!=null){
								debug.append( "\""+result[0]+"\"" );
							}
							
							return( result );
							
						}else{
						
							Object o_result = getKeywordValue( dm, tags, str );
								
							if ( o_result == null ){
			
								throw( new Exception( "Invalid constraint string: " + str ));
								
							}else if ( o_result instanceof String  ){
								
								result = new String[]{ (String)o_result };
								
							}else if ( o_result instanceof String[] ){
								
								result = (String[])o_result;
								
							}else{
								
								throw( new Exception( "Invalid constraint keyword, string(s) expected: " + str ));
							}
							
							if ( debug!=null){
								debug.append( "[" );
								debug.append( str );
								debug.append( "->\"" );
								
								debug.append( result[0] );
								
								if ( result.length > 1 ){
									debug.append( ",..." );
								}
								
								debug.append( "\"]" );
							}
						
							return( result );
						}
						
					}else if ( arg instanceof ConstraintExpr ){		
	
						if ( debug!=null){
							debug.append( "[" );
						}
						
						String res = (String)((ConstraintExpr)arg).eval( context, dm, tags, debug );
						
						if ( debug!=null){
							debug.append( "->" + res + "]" );
						}
						
						return( new String[]{ res });
						
					}else{
						
						throw( new Exception( "Invalid constraint string: " + arg ));
					}
				}catch( Throwable e ){
					
					setError( Debug.getNestedExceptionMessage( e ));
					
					String result = "\"\"";

					args[index] = result;

					return( new String[]{ result });
				}
			}
			
			private Object
			getKeywordValue(
				DownloadManager		dm,
				List<Tag>			tags,
				String				str )
			{
				int[] kw_details = keyword_map.get( str.toLowerCase( Locale.US ));

				if ( kw_details == null ){
					
					return( null );
				}
				
				int kw = kw_details[0];
				
				switch( kw ){
					case KW_SHARE_RATIO:{
	
						int sr = dm.getStats().getShareRatio();
	
						if ( sr == -1 ){
	
							return( Integer.MAX_VALUE );
	
						}else{
	
							return( new Float( sr/1000.0f ));
						}
					}
					case KW_TARGET_RATIO:{
	
						int tr = dm.getDownloadState().getIntParameter( DownloadManagerState.PARAM_MAX_SHARE_RATIO );
	
						if ( tr <= 0 ){
	
							tr = target_share_ratio;
						}
	
						return( new Float( tr/1000.0f ));
					}
	
					case KW_PERCENT:{
	
							// 0->1000
	
						int percent = dm.getStats().getPercentDoneExcludingDND();
	
						return( new Float( percent/10.0f ));
					}
					case KW_AGE:{
	
						long added = dm.getDownloadState().getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_ADDED_TIME );
	
						if ( added <= 0 ){
	
							return( 0 );
						}
	
						return(( SystemTime.getCurrentTime() - added )/1000 );		// secs
					}
					case KW_COMPLETED_AGE:{
	
						long comp = dm.getDownloadState().getLongParameter( DownloadManagerState.PARAM_DOWNLOAD_COMPLETED_TIME );
	
						if ( comp <= 0 ){
	
							return( 0 );
						}
	
						return(( SystemTime.getCurrentTime() - comp )/1000 );		// secs
					}
					case KW_PEER_MAX_COMP:{
	
						PEPeerManager pm = dm.getPeerManager();
						
						if ( pm == null ){
							
							return( 0 );
						}
						
						return(	new Float( pm.getMaxCompletionInThousandNotation( false )/10.0f ));
					}
					case KW_LEECHER_MAX_COMP:{
	
						PEPeerManager pm = dm.getPeerManager();
						
						if ( pm == null ){
							
							return( 0 );
						}
						
						return(	new Float( pm.getMaxCompletionInThousandNotation( true )/10.0f ));
					}
	
					case KW_PEER_AVERAGE_COMP:{
	
						PEPeerManager pm = dm.getPeerManager();
						
						if ( pm == null ){
							
							return( 0 );
						}
						
						return(	new Float( pm.getAverageCompletionInThousandNotation()/10.0f ));
					}
					case KW_DOWNLOADING_FOR:{
	
						return( dm.getStats().getSecondsDownloading());
					}
					case KW_SEEDING_FOR:{
	
						return( dm.getStats().getSecondsOnlySeeding());
					}
					case KW_LAST_ACTIVE:{
	
						DownloadManagerState dms = dm.getDownloadState();
	
						long	timestamp = dms.getLongAttribute( DownloadManagerState.AT_LAST_ADDED_TO_ACTIVE_TAG );
	
						if ( timestamp <= 0 ){
	
							return( Long.MAX_VALUE );
						}
	
						return(( SystemTime.getCurrentTime() - timestamp )/1000 );
					}
					case KW_RESUME_IN:{
	
						long resume_millis = dm.getAutoResumeTime();
	
						long	now = SystemTime.getCurrentTime();
	
						if ( resume_millis <= 0 || resume_millis <= now ){
	
							return( 0 );
						}
	
						return(( resume_millis - now )/1000 );
					}
					case KW_MIN_OF_HOUR:{
	
						long	now = SystemTime.getCurrentTime();
	
						GregorianCalendar cal = new GregorianCalendar();
	
						cal.setTime( new Date( now ));
	
						return( cal.get( Calendar.MINUTE ));
					}
					case KW_HOUR_OF_DAY:{
	
						long	now = SystemTime.getCurrentTime();
	
						GregorianCalendar cal = new GregorianCalendar();
	
						cal.setTime( new Date( now ));
	
						return( cal.get( Calendar.HOUR_OF_DAY ));
					}
					case KW_DAY_OF_WEEK:{
	
						long	now = SystemTime.getCurrentTime();
	
						GregorianCalendar cal = new GregorianCalendar();
	
						cal.setTime( new Date( now ));
	
						return( cal.get( Calendar.DAY_OF_WEEK ));
					}
					case KW_SWARM_MERGE:{
	
						return( dm.getDownloadState().getLongAttribute( DownloadManagerState.AT_MERGED_DATA ));
					}
					case KW_SEED_COUNT:{
	
						TRTrackerScraperResponse response = dm.getTrackerScrapeResponse();
	
						int	seeds = dm.getNbSeeds();
	
						if ( response != null && response.isValid()){
	
							seeds = Math.max( seeds, response.getSeeds());
						}
	
						Download dl = PluginCoreUtils.wrap( dm );
	
						if ( dl != null ){
							
							seeds = Math.max( seeds, dl.getAggregatedScrapeResult().getSeedCount());
						}
						
						return( Math.max( 0, seeds ));
					}
					case KW_PEER_COUNT:{
	
						TRTrackerScraperResponse response = dm.getTrackerScrapeResponse();
	
						int	peers = dm.getNbPeers();
	
						if ( response != null && response.isValid()){
	
							peers = Math.max( peers, response.getPeers());
						}
	
						Download dl = PluginCoreUtils.wrap( dm );
	
						if ( dl != null ){
							
							peers = Math.max( peers, dl.getAggregatedScrapeResult().getNonSeedCount());
						}
						
						return( Math.max( 0, peers ));
					}
					case KW_SEED_PEER_RATIO:{
	
						TRTrackerScraperResponse response = dm.getTrackerScrapeResponse();
	
						int	seeds = dm.getNbSeeds();
						int	peers = dm.getNbPeers();
	
						if ( response != null && response.isValid()){
	
							seeds = Math.max( seeds, response.getSeeds());
							peers = Math.max( peers, response.getPeers());
						}
	
						Download dl = PluginCoreUtils.wrap( dm );
	
						if ( dl != null ){
							
							DownloadScrapeResult sr = dl.getAggregatedScrapeResult();
							
							seeds = Math.max( seeds, sr.getSeedCount());
							peers = Math.max( peers, sr.getNonSeedCount());
						}
						
						float ratio;
	
						if ( peers < 0 || seeds < 0 ){
	
							ratio = 0;
	
						}else{
	
							if ( peers == 0 ){
	
								if ( seeds == 0 ){
	
									ratio = 0;
	
								}else{
	
									return( Integer.MAX_VALUE );
								}
							}else{
	
								ratio = (float)seeds/peers;
							}
						}
	
						return( ratio );
					}
					case KW_TAG_AGE:{
	
						long tag_added = tag_maybe_null.getTaggableAddedTime( dm );
	
						if ( tag_added <= 0 ){
	
							return( 0 );
						}
	
						long age = (( SystemTime.getCurrentTime() - tag_added )/1000 );		// secs
	
						if ( age < 0 ){
	
							age = 0;
						}
	
						return( age );
					}
	
					case KW_SIZE:{
						
						return( dm.getSize());
					}
					case KW_SIZE_MB:{
						
							// hmm, should be 1000 for MB and 1024 for MiB but legacy
						
						return( dm.getSize()/(1024*1024L));
					}
					case KW_SIZE_GB:{
						
						return( dm.getSize()/(1024*1024*1024L));
					}
					case KW_FILE_COUNT:{
						
						return( dm.getNumFileInfos());
					}
					case KW_AVAILABILITY:{
	
						PEPeerManager pm = dm.getPeerManager();
						
						if ( pm == null ){
							
							return( -1f );
						}
						
						float avail = pm.getMinAvailability();
						
						return(	new Float( avail ));
					}
					case KW_UP_IDLE:{
						
						long secs = dm.getStats().getTimeSinceLastDataSentInSeconds();
						
						if ( secs < 0 ){
							
							return( Long.MAX_VALUE );
							
						}else{
							
							return( secs );
						}
					}
					case KW_DOWN_IDLE:{
						
						long secs = dm.getStats().getTimeSinceLastDataReceivedInSeconds();
						
						if ( secs < 0 ){
							
							return( Long.MAX_VALUE );
							
						}else{
							
							return( secs );
						}
					}
					case KW_DOWNLOADED:{
						
						return( dm.getStats().getTotalGoodDataBytesReceived());
					}
					case KW_REMAINING:{
						
						return( dm.getStats().getRemainingExcludingDND());
					}
					case KW_UPLOADED:{
						
						return( dm.getStats().getTotalDataBytesSent());
					}
					case KW_MAX_UP:{
						
						return( dm.getStats().getUploadRateLimitBytesPerSecond());
					}
					case KW_MAX_DOWN:{
						
						return( dm.getStats().getDownloadRateLimitBytesPerSecond());
					}
					case KW_TORRENT_TYPE:{
						
						TOTorrent t = dm.getTorrent();
	
						return( t==null?0:t.getTorrentType());
					}
					case KW_FULL_COPY_SEEN:{
						
						long value = dm.getStats().getAvailWentBadTime();
						
						if ( value < 0 ){
							
								// never seen a full copy
							
							value = Long.MIN_VALUE;
							
						}else if ( value == 0 ){
							
								// currently good
							
							value = SystemTime.getCurrentTime();
						}
						
						return( value );
					}
					case KW_DOWN_SPEED:
					case KW_UP_SPEED:{
	
						long[] rates = (long[])dm.getUserData( DM_RATES );
						
						if ( rates == null ){
							
							rates = new long[]{ -1, -1, 0, 0 };
							
							dm.setUserData( DM_RATES, rates );
							
							return( 0 );
							
						}else if ( rates[0] == -1 ){
							
							return( 0 );
							
						}else{
							
							return( rates[kw==KW_DOWN_SPEED?2:3]);
						}
					}
					case KW_SESSION_AGE:{
						
						long value = dm.getStats().getTimeStarted();
						
						if ( value <= 0 ){
							
							return( 0 );
							
						}else{
							
							return((SystemTime.getCurrentTime() - value )/1000 );
						}
					}
					case KW_NAME:{
					
						dm.setUserData( DM_NAME, "" );	// just a marker
						
						depends_on_names_etc = true;
						
						return( dm.getDisplayName());
					}
					case KW_FILE_NAMES:{
										
						String[] result = (String[])dm.getUserData( DM_FILE_NAMES );
						
						if ( result == null ){
							
							DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
							
							result = new String[files.length];
							
							for ( int i=0;i<files.length;i++){
								
								result[i] = files[i].getFile( false ).getName();
							}
							
							dm.setUserData( DM_FILE_NAMES, result );
							
							depends_on_names_etc = true;
						}
						
						return( result );
					}
					case KW_FILE_EXTS:{
												
						String[] result = (String[])dm.getUserData( DM_FILE_EXTS);
						
						if ( result == null ){
							
							DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
							
							Set<String>	exts = new HashSet<>();
							
							for ( int i=0;i<files.length;i++){
								
								String ext = files[i].getExtension();
								
								if ( ext != null && !ext.isEmpty() && !exts.contains( ext )){
									
									exts.add( ext.toLowerCase( Locale.US ));
								}
							}
							
							result = exts.toArray( new String[0] );
							
							dm.setUserData( DM_FILE_EXTS, result );
							
							depends_on_names_etc = true;
						}
						
						return( result );
					}
					case KW_FILE_PATHS:{
					
						String[] result = (String[])dm.getUserData( DM_FILE_PATHS );
						
						if ( result == null ){
							
							DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
							
							result = new String[files.length];
							
							for ( int i=0;i<files.length;i++){
								
								result[i] = files[i].getFile( true ).getAbsolutePath();
							}					
							
							dm.setUserData( DM_FILE_PATHS, result );
							
							depends_on_names_etc = true;
							
							handler.checkDMListeners( dm );
						}
						
						return( result );
					}
					case KW_FILE_EXTS_SELECTED:{
						
						String[] result = (String[])dm.getUserData( DM_FILE_EXTS_SELECTED );
						
						if ( result == null ){
							
							DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
							
							Set<String>	exts = new HashSet<>();
							
							for ( int i=0;i<files.length;i++){
								
								if ( files[i].isSkipped()){
									
									continue;
								}
								
								String ext = files[i].getExtension();
								
								if ( ext != null && !ext.isEmpty() && !exts.contains( ext )){
									
									exts.add( ext.toLowerCase( Locale.US ));
								}
							}
							
							result = exts.toArray( new String[0] );
							
							dm.setUserData( DM_FILE_EXTS_SELECTED, result );
							
							depends_on_names_etc = true;
							
							handler.checkDMListeners( dm );
						}
						
						return( result );
					}
					case KW_FILE_NAMES_SELECTED:{
					
						String[] result = (String[])dm.getUserData( DM_FILE_NAMES_SELECTED );
						
						if ( result == null ){
							
							DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
							
							List<String>	names = new ArrayList<>( files.length );
							
							for ( int i=0;i<files.length;i++){
								
								if ( files[i].isSkipped()){
									
									continue;
								}
								
								names.add( files[i].getFile( false ).getName());
							}
							
							result = names.toArray( new String[0] );
							
							dm.setUserData( DM_FILE_NAMES_SELECTED, result );
							
							depends_on_names_etc = true;
							
							handler.checkDMListeners( dm );
						}
					
						return( result );
					}
					case KW_FILE_COUNT_SELECTED:{
						
						String[] result = (String[])dm.getUserData( DM_FILE_NAMES_SELECTED );
						
						if ( result == null ){
							
							DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
							
							List<String>	names = new ArrayList<>( files.length );
							
							for ( int i=0;i<files.length;i++){
								
								if ( files[i].isSkipped()){
									
									continue;
								}
								
								names.add( files[i].getFile( false ).getName());
							}
							
							result = names.toArray( new String[0] );
							
							dm.setUserData( DM_FILE_NAMES_SELECTED, result );
							
							depends_on_names_etc = true;
							
							handler.checkDMListeners( dm );
						}
					
						return( result.length );
					}

					case KW_TAG_NAMES:{
					
						String[] result = new String[tags.size()];
						
						for ( int i=0;i<tags.size();i++){
							Tag tag = tags.get(i);
							
							if ( tag == tag_maybe_null ){
								result[i] = "";
							}else{
								result[i] = tag.getTagName( true );
							}
						}
						
						return( result );
					}
					case KW_TRACKER_STATUS:{
					
						String result = dm.getTrackerStatus();
						
						return( result );
					}
					case KW_FILE_PATHS_SELECTED:{
					
						String[] result = (String[])dm.getUserData( DM_FILE_PATHS_SELECTED );
						
						if ( result == null ){
							
							DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
							
							List<String>	names = new ArrayList<>( files.length );
							
							for ( int i=0;i<files.length;i++){
								
								if ( files[i].isSkipped()){
									
									continue;
								}
								
								names.add( files[i].getFile( true ).getAbsolutePath());
							}
							
							result = names.toArray( new String[0] );
							
							dm.setUserData( DM_FILE_PATHS_SELECTED, result );
							
							depends_on_names_etc = true;
							
							handler.checkDMListeners( dm );
						}
						
						return( result );
					}
					case KW_SAVE_PATH:{

						depends_on_names_etc = true;
						
						dm.setUserData( DM_SAVE_PATH, "" );	// just a marker
						
						return( dm.getAbsoluteSaveLocation().getAbsolutePath());
					}
					case KW_SAVE_FOLDER:{
										
						depends_on_names_etc = true;
						
						dm.setUserData( DM_SAVE_PATH, "" );	// just a marker
	
						File save_loc = dm.getAbsoluteSaveLocation().getAbsoluteFile();
						
						File save_folder = save_loc.getParentFile();
						
						if ( FileUtil.isDirectoryWithTimeout(save_folder)){
							
							return( save_folder.getAbsolutePath());
							
						}else{
							
							return( save_loc.getAbsolutePath());
						}
					}
					case KW_MOC_PATH:{
						
						String result = dm.getDownloadState().getAttribute( DownloadManagerState.AT_MOVE_ON_COMPLETE_DIR );
						
						if ( result == null || result.isEmpty()){
								
							result = null;
							
							List<Tag> moc_tags = TagUtils.getActiveMoveOnCompleteTags( dm, true, (s)->{});
							
							for ( Tag tag: moc_tags ){
																
								TagFeatureFileLocation fl = (TagFeatureFileLocation)tag;
														
								long	options = fl.getTagMoveOnCompleteOptions();
					
								if ( ( options&TagFeatureFileLocation.FL_DATA ) != 0 ){
									
									File move_to_target = fl.getTagMoveOnCompleteFolder();
									
									if ( move_to_target != null ){
										
										result = move_to_target.getAbsolutePath();
										
										break;
									}
								}
							}
						}
						
						if ( result == null ){
							
							result = ConfigUtils.getDefaultMoveOnCompleteFolder();
						}

						return( result==null?"":result );
					}
					case KW_PLUGIN_MY_RATING:{
						
						if ( handler.ta_rating_name == null ){
							
							return( 0 );
						}
						
						String rating_str = dm.getDownloadState().getAttribute( handler.ta_rating_name );
						
						if ( rating_str != null ){
							
							return( Integer.parseInt(rating_str));
							
						}else{
							
							return( 0 );
						}
					}
					case KW_MAX32:{
						return( Integer.MAX_VALUE );
					}
					case KW_MIN32:{
						return( Integer.MIN_VALUE );
					}
					case KW_MAX64:{
						return( Long.MAX_VALUE );
					}
					case KW_MIN64:{
						return( Long.MIN_VALUE );
					}
					case KW_TRACKERS:{
						
						TOTorrent torrent = dm.getTorrent();

						if ( torrent == null ){
							
							return( new String[0] );
						}
						
						URL	announce = torrent.getAnnounceURL();
						
						TOTorrentAnnounceURLGroup group = torrent.getAnnounceURLGroup();

						TOTorrentAnnounceURLSet[] sets = group.getAnnounceURLSets();

						Object[] cache = (Object[])dm.getUserData( DM_TRACKERS );
						
						if ( cache != null ){
															
							if ( sets.length == 0 ){
								
								if ( cache[0] == announce ){
									
									return((String[])cache[1]);
								}
							}else{
								
								Object o = cache[0];
								
								if ( o instanceof Long && ((Long)o) == group.getUID()){
									
									return((String[])cache[1]);
								}
							}
						}
													
						cache = new Object[2];
						
						if ( sets.length == 0 ){
							
							String result = announce.getHost();
							
							int port = announce.getPort();
							
							if ( port == -1 ){
								
								port = announce.getDefaultPort();
							}
							
							if ( port >= 0 ){
								
								result += ":" + port;
							}
							
							cache[0] = announce;
							cache[1] = new String[]{ result };
							
						}else{
							
							Set<String> results = TorrentUtils.getUniqueTrackerHosts( torrent, true );
							
							cache[0] = group.getUID();
							cache[1] = results.toArray( new String[results.size()] );
						}
						
						dm.setUserData( DM_TRACKERS, cache );
						
						return((String[])cache[1] );
					}
					default:{
						
						return( null );
					}
				}
			}
			
			private Number
			getNumeric(
				Map<String,Object>	context,
				DownloadManager		dm,
				List<Tag>			tags,
				Object[]			args,
				int					index,
				StringBuilder		debug )
			{
				if ( debug!=null&&index>0){
					debug.append( "," );
				}
				
				Object arg = args[index];
				
				if ( arg instanceof Number ){

					Number res = (Number)arg;
					
					if ( debug!=null){
						debug.append( "[" + res + "]" );
					}			
					
					return( res );
					
				}else if ( arg instanceof ConstraintExpr ){		

					if ( debug!=null){
						debug.append( "[" );
					}
					
					Object res = ((ConstraintExpr)arg).eval( context, dm, tags, debug );
					
					if ( debug!=null){
						debug.append( "->" + res + "]" );
					}
					
					if ( res instanceof Number ){
						
						return((Number)res);
						
					}else if ( res instanceof Boolean ){
						
						return(((Boolean)res).booleanValue()?1:0);
						
					}else if ( res instanceof String ){
						
						return( Long.parseLong((String)res));
						
					}else{

						throw( new RuntimeException( "Unsupported numeric type: " + res ));
					}
					
				}else{

					if ( debug!=null){
						debug.append( "[" + arg + "->" );
					}			

					Number res = getNumericSupport( dm, tags, args, index );
					
					if ( debug!=null){
						debug.append( res + "]" );
					}
	
					return( res );
				}
			}
			
			private Number
			getNumericSupport(
				DownloadManager		dm,
				List<Tag>			tags,
				Object[]			args,
				int					index )
			{
				Object arg = args[index];
				
				String str = (String)arg;

				Number result = 0;

				try{
					if ( str.equals( Constants.INFINITY_STRING )){
						
						result = Integer.MAX_VALUE;
						
						return( result );
						
					}else if ( 	Character.isDigit( str.charAt(0)) || 
								( 	str.length() > 1 && 
									str.startsWith( "-") && 
									Character.isDigit( str.charAt(1)))){

							// look for units
						
						String unit = "";
						
							// start at one to skip any potential leading -
						
						for ( int i=1;i<str.length();i++){
							
							char c = str.charAt( i );
							
							if ( c != '.' && !Character.isDigit( c )){
								
								unit = str.substring( i ).trim();
								
								str = str.substring( 0,  i );
								
								break;
							}
						}
						
						if ( str.contains( "." )){

							result = Double.parseDouble( str );

						}else{

							result = Long.parseLong( str );
						}

						if ( !unit.isEmpty()){
							
							long multiplier = GeneralUtils.getUnitMultiplier( unit, false );
							
							if ( multiplier <= 0 ){
								
								setError( "Invalid unit '" + unit + "'" );
								
							}else{
								
								if ( multiplier > 1 ){
									
									if ( result instanceof Long ){
										
										result = result.longValue() * multiplier;
										
									}else{
										
										result = result.doubleValue() * multiplier;
									}
								}
							}
						}
						return( result );
						
					}else{

						result = null;	// don't cache any results below as they are variable

						Object o_result = getKeywordValue( dm, tags, str );
							
						if ( o_result instanceof Number ){
							
							return((Number)o_result);
						}
						
						if ( o_result == null ){
						
							setError( "Invalid constraint keyword: " + str );
							
						}else{
							
							setError( "Invalid constraint keyword, numeric expected: " + str );
						}

						return( result );
					}
				}catch( Throwable e){

					setError( "Invalid constraint numeric: " + str );

					return( result );

				}finally{

					if ( result != null ){

							// cache literal results

						args[index] = result;
					}
				}
			}

			@Override
			public String
			getString()
			{
				return( func_name + "(" + params_expr.getString() + ")" );
			}
		}
	}
}
