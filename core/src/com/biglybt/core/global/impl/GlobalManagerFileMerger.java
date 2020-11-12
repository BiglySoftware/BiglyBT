/*
 * Created on Feb 2, 2015
 * Created by Paul Gardner
 *
 * Copyright 2015 Azureus Software, Inc.  All rights reserved.
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


package com.biglybt.core.global.impl;

import java.util.*;


import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerFileInfoListener;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerPeerListener;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManagerAdapter;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerManagerListenerAdapter;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginAdapter;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.components.UIButton;
import com.biglybt.pif.ui.components.UIComponent;
import com.biglybt.pif.ui.components.UIPropertyChangeEvent;
import com.biglybt.pif.ui.components.UIPropertyChangeListener;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.pifimpl.local.PluginInitializer;


public class
GlobalManagerFileMerger
	implements AEDiagnosticsEvidenceGenerator
{
	private static final int HASH_FAILS_BEFORE_QUIT	= 3;

	private static final int TIMER_PERIOD				= 5*1000;
	private static final int FORCE_PIECE_TIMER_PERIOD	= 15*1000;
	private static final int FORCE_PIECE_TIMER_TICKS	= FORCE_PIECE_TIMER_PERIOD/TIMER_PERIOD;
	private static final int SYNC_TIMER_PERIOD			= 60*1000;
	private static final int SYNC_TIMER_TICKS			= SYNC_TIMER_PERIOD/TIMER_PERIOD;

	private static final String	ORIGINATOR_PREFIX = "SwarmMerge-xfer";
	
	static final Object merged_data_lock = new Object();

	private final GlobalManagerImpl		gm;

	private LoggerChannel 	log;
	private boolean			logging_paused = true;
	
	boolean	initialised;
	boolean	enabled;
	boolean	enabled_extended;
	
	int		tolerance	= 0;
	int		min_pieces	= 5;

	final Map<HashWrapper,DownloadManager>		dm_map = new HashMap<>();

	final List<SameSizeFiles>				sames 		= new ArrayList<>();
	final Set<DownloadManager>				sames_dms	= new IdentityHashSet<>();
	
	final AsyncDispatcher		read_write_dispatcher 	= new AsyncDispatcher( "GMFM" );
	final AsyncDispatcher 		sync_dispatcher 		= new AsyncDispatcher( "GMFM:serial" );

	private TimerEventPeriodic	timer_event;

	protected
	GlobalManagerFileMerger(
		GlobalManagerImpl			_gm )
	{
		gm		= _gm;

		PluginInitializer.getDefaultInterface().addListener(
			new PluginAdapter()
			{
				@Override
				public void
				initializationComplete()
				{
					initialise();
				}
			});
	}

	void
	initialise()
	{
		AEDiagnostics.addEvidenceGenerator( this );
		
		String VIEW_NAME =  "TableColumn.header.mergeddata";
		
		PluginInterface plugin_interface = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface();
		
		log = plugin_interface.getLogger().getChannel( "Swarm Merge" );

		log.setDiagnostic();

		log.setForce( true );

		String pause_key = VIEW_NAME + ".LoggerView.pause";
		
		COConfigurationManager.setParameter( pause_key, true );
		
		COConfigurationManager.addParameterListener(
			pause_key,
			new ParameterListener(){
				
				@Override
				public void parameterChanged(String name ){
				
					setLoggingPaused( COConfigurationManager.getBooleanParameter( name ));
				}
			});
		
		UIManager	ui_manager = plugin_interface.getUIManager();

		BasicPluginViewModel model = ui_manager.createBasicPluginViewModel(VIEW_NAME );

		model.setProperty( BasicPluginViewModel.PR_EXTERNAL_LOG_PAUSE, true );
		
		model.getStatus().setText( "Starting up..." );
		
		model.getActivity().setVisible( false );
		model.getProgress().setVisible( false );

		UIButton reset = model.addButton();
		
		reset.setLabel( "TableColumn.header.mergeddata" );
		
		reset.setName( "Button.reset" );
		
		reset.addPropertyChangeListener(
			new UIPropertyChangeListener(){
				
				@Override
				public void propertyChanged(UIPropertyChangeEvent ev){
					if ( ev.getPropertyType() == UIComponent.PT_SELECTED ){
						
						log( "Resetting" );
						
						synchronized( dm_map ){

							for ( SameSizeFiles s: sames ){

								s.destroy();
							}
							
							sames.clear();
							
							sames_dms.clear();
							
							syncFileSets( true );
						}
					}
				}
			});
		
		model.attachLoggerChannel( log );

		setLoggingPaused( true );
		
		new DelayedEvent(
				"GMFM:delay",
				30*1000,
				new AERunnable() {

					@Override
					public void
					runSupport()
					{
						COConfigurationManager.addAndFireParameterListeners(
							new String[]{ 
									"Merge Same Size Files", 
									"Merge Same Size Files Extended", 
									"Merge Same Size Files Tolerance",
									ConfigKeys.File.ICFG_MERGE_SAME_SIZE_FILES_MIN_PIECES },
							new ParameterListener(){
				
								@Override
								public void
								parameterChanged(
									String name )
								{
									enabled 			= COConfigurationManager.getBooleanParameter( "Merge Same Size Files" );
									enabled_extended 	= COConfigurationManager.getBooleanParameter( "Merge Same Size Files Extended" );
									
									model.getStatus().setText( enabled?"Running":"Disabled" );
				
									int	old_tolerance 	= tolerance;
									int old_min_pieces	= min_pieces;
									
									tolerance		 	= COConfigurationManager.getIntParameter( "Merge Same Size Files Tolerance" );
									min_pieces		 	= COConfigurationManager.getIntParameter( ConfigKeys.File.ICFG_MERGE_SAME_SIZE_FILES_MIN_PIECES );
				
									logSupport( "Complete files=" + enabled_extended + ", tolerance=" + tolerance + ", min pieces=" + min_pieces );
									
									if ( initialised ){
				
										syncFileSets( old_tolerance != tolerance || old_min_pieces != min_pieces );
									}
								}
							});
				
						gm.addListener(
							new GlobalManagerAdapter()
							{
								@Override
								public void
								downloadManagerAdded(
									DownloadManager dm )
								{
									syncFileSets( false );
								}
				
								@Override
								public void
								downloadManagerRemoved(
									DownloadManager dm )
								{
									syncFileSets( false );
								}
							},
							false );
				
						syncFileSets( false );
				
						initialised = true;
						
					}
				});
	}

	void
	setLoggingPaused(
		boolean	b )
	{
		logging_paused = b;
		
		logSupport( "Paused=" + b );
		
		if ( !b ){
			
			logCurrentState( null );
		}
	}
	
	void 
	log(
		String			str )
	{
		log( null, str );
	}
	
	void 
	log(
		IndentWriter	writer,
		String			str )
	{
		if ( writer == null ){
			
			if ( !logging_paused ){
				
				logSupport( str );
			}
		}else{
			
			writer.println( str );
		}
	}
	
	void 
	logSupport(
		String	str )
	{
		log.log( str );
	}
	
	protected boolean
	isSwarmMergingZ(
		DownloadManager		dm )
	{
		synchronized( dm_map ){

			return( sames_dms.contains( dm ));
		}
	}

	protected String
	getSwarmMergingInfo(
		DownloadManager		dm )
	{
		synchronized( dm_map ){

			if ( sames.size() > 0 ){

				StringBuffer	result = null;

				for ( SameSizeFiles s: sames ){

					if ( s.hasDownloadManager( dm )){

						String info = s.getInfo();

						if ( result == null ){

							result = new StringBuffer( 1024 );

						}else{

							result.append( "\n" );
						}

						result.append( info );
					}
				}

				return( result==null?null:result.toString());
			}
		}

		return( null );
	}
	
	void
	syncFileSets(
		boolean		force )
	{
		List<DownloadManager> dms = gm.getDownloadManagers();

		synchronized( dm_map ){

			boolean	changed = false;

			Set<HashWrapper>	existing_dm_hashes = new HashSet<>(dm_map.keySet());

			if ( enabled ){

				log( "Scanning files" );
				
				for ( DownloadManager dm: dms ){

					/* 
					 * not sure why we were ignoring shares, one might share a local file in order to help
					 * out a 'normal' download
					 * 
					if ( !dm.isPersistent()){

						continue;
					}
					*/
					
					DownloadManagerState state = dm.getDownloadState();

					if ( 	state.getFlag( DownloadManagerState.FLAG_LOW_NOISE ) ||
							state.getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){

						continue;
					}

					if ( enabled_extended || !dm.isDownloadComplete( false )){

						TOTorrent torrent = dm.getTorrent();

						if ( torrent != null ){

							try{
								HashWrapper hw = torrent.getHashWrapper();

								if ( dm_map.containsKey( hw )){

									existing_dm_hashes.remove( hw );

								}else{

									dm_map.put( hw, dm );

									changed = true;
								}
							}catch( Throwable e ){
							}
						}
					}
				}
			}

			if ( existing_dm_hashes.size() > 0 ){

				changed = true;

				for ( HashWrapper hw: existing_dm_hashes ){

					dm_map.remove( hw );
				}
			}

			if ( changed || force ){
								
				List<Set<DiskManagerFileInfo>>			interesting = new LinkedList<>();

				Set<DownloadManager> merging_downloads = new IdentityHashSet<>();	
								
				for ( int loop=0;loop<2;loop++){
					
					List<Set<DiskManagerFileInfo>>			interesting_root_hashes = new LinkedList<>();

					ByteArrayHashMap<Set<DiskManagerFileInfo>>		root_hash_map = new ByteArrayHashMap<>();

					for ( DownloadManager dm: dm_map.values()){
						
						TOTorrent torrent = dm.getTorrent();

						if ( torrent == null ){

							continue;
						}
						
						if ( torrent.getTorrentType() == TOTorrent.TT_V1 ){
							
							continue;
						}

						DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();

						boolean dm_is_merging = merging_downloads.contains( dm );
								
						for ( DiskManagerFileInfo file: files ){
							
								// filter out small files

							if ( file.getLength() == 0 || ( file.getNbPieces() < min_pieces && !dm_is_merging )){

								continue;
							}
							
							TOTorrentFile torrent_file = file.getTorrentFile();
							
							if ( torrent_file.isPadFile()){
								
								continue;
							}
							
							byte[] root_hash = torrent_file.getRootHash();
							
							if ( root_hash != null ){
								
								Set<DiskManagerFileInfo> set = root_hash_map.get( root_hash );
								
								if ( set == null ){
									
									set = new HashSet<>();
									
									root_hash_map.put( root_hash, set );
								}
								
								set.add( file );
								
								if ( set.size() == 2 ){
									
									interesting_root_hashes.add( set );
								}
							}
						}
					}
					
					Set<DiskManagerFileInfo>	files_already_allocated = new HashSet<>();
					
						// currently if we have hash based same-sets then we don't consider them against non-hash based files of same size
						// as quite unlikely scenario and makes things simple
					
					if ( !interesting_root_hashes.isEmpty()){
										
						interesting.addAll( interesting_root_hashes );
						
						for ( Set<DiskManagerFileInfo> set: interesting_root_hashes ){
							
							files_already_allocated.addAll( set );
						}
					}
									
					
					Map<String,Object>						tolerance_map = new HashMap<>();

					Map<Long,Set<DiskManagerFileInfo>>		size_map = new HashMap<>();

					if ( tolerance != 0 ){
						
						for ( DownloadManager dm: dm_map.values()){
	
							TOTorrent torrent = dm.getTorrent();
	
							if ( torrent == null ){
	
								continue;
							}
	
							DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
	
							boolean dm_is_merging = merging_downloads.contains( dm );
									
							for ( DiskManagerFileInfo file: files ){
	
								if ( files_already_allocated.contains( file )){
									
									continue;
								}
								
								if ( file.getTorrentFile().isPadFile()){
									
									continue;
								}
								
									// filter out small files
	
								if ( file.getLength() == 0 || ( file.getNbPieces() < min_pieces && !dm_is_merging )){
	
									continue;
								}
									
								String name = file.getFile( true ).getName();
								
								long	len = file.getLength();
								
								Object existing = tolerance_map.get( name );
								
								if ( existing == null ){
									
									tolerance_map.put( name, len );
									
								}else{
									
									if ( existing instanceof Long ){
										
										List<Long> list = new ArrayList<>(2);
								
										list.add((Long)existing );
										list.add( len );
										
										tolerance_map.put( name, list );
									}else{
										
										((List<Long>)existing).add( len );
									}
								}
							}
						}
					}
	
					for ( DownloadManager dm: dm_map.values()){
	
						TOTorrent torrent = dm.getTorrent();
	
						if ( torrent == null ){
	
							continue;
						}
	
						DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
	
						boolean dm_is_merging = merging_downloads.contains( dm );

						for ( DiskManagerFileInfo file: files ){
	
							if ( files_already_allocated.contains( file )){
								
								continue;
							}
							
							if ( file.getTorrentFile().isPadFile()){
								
								continue;
							}

								// filter out small files
	
							if ( file.getLength() == 0 || ( file.getNbPieces() < min_pieces && !dm_is_merging )){
	
								continue;
							}
	
							long len_to_use = file.getLength();
	
							if ( tolerance != 0 ){
								
								String name = file.getFile( true ).getName();
								
								Object o = tolerance_map.get( name );
								
								if ( o instanceof Long ){
									
								}else{
									
									Long[]	lengths;
									
									if ( o instanceof List ){
									
										List<Long>	list = (List<Long>)o;
										
										lengths = list.toArray( new Long[0] );
										
										Arrays.sort( lengths );
										
										tolerance_map.put( name, lengths );
										
									}else{
										
										lengths = (Long[])o;
										
									}
									
									long	current = lengths[0];
									
									if ( len_to_use > current ){
										
										for ( int i=1;i<lengths.length;i++){
											
											long	l = lengths[i];
											
											if ( l - current > tolerance ){
												
												current = l;
											}
											
											if ( l == len_to_use ){
												
												len_to_use = current;
												
												break;
											}
										}
									}
								}
							}
							
							Set<DiskManagerFileInfo> set = size_map.get( len_to_use );
	
							if ( set == null ){
	
								set = new HashSet<>();
	
								size_map.put( len_to_use, set );
							}
	
							boolean same_dm = false;
	
							for ( DiskManagerFileInfo existing: set ){
	
								if ( existing.getDownloadManager() == dm ){
	
									same_dm = true;
	
									break;
								}
							}
	
							if ( !same_dm ){
	
								set.add( file );
	
								if ( set.size() == 2 ){
	
									interesting.add( set );
								}
							}
						}
					}
						
					if ( loop == 0 && min_pieces > 0 && !interesting.isEmpty()){
						
							// do it all again but this time include all matching files from interesting downloads
						
						Iterator<Set<DiskManagerFileInfo>> interesting_it = interesting.iterator();
						
						while( interesting_it.hasNext()){
		
							Set<DiskManagerFileInfo> set = interesting_it.next();
				
							for ( DiskManagerFileInfo file: set ){
								
								merging_downloads.add( file.getDownloadManager());
							}
						}
						
						log( "Rescanning after finding " + merging_downloads.size() + " interesting downloads with " + interesting.size() + " same-size files" );

						interesting.clear();
						
					}else{
						
						break;
					}
				}
					
					// remove sets consisting of only completed files
					
				Iterator<Set<DiskManagerFileInfo>>	interesting_it = interesting.iterator();
	
				while( interesting_it.hasNext()){
	
					Set<DiskManagerFileInfo> set = interesting_it.next();
	
					boolean all_done = true;
	
					for ( DiskManagerFileInfo file: set ){
	
						if ( file.getDownloaded() != file.getLength()){
	
							all_done = false;
	
							break;
						}
					}
	
					if ( all_done ){
	
						interesting_it.remove();
					}
				}

				List<SameSizeFiles>	sames_copy = new LinkedList<>(sames);

				for ( Set<DiskManagerFileInfo> set: interesting ){

					boolean	found = false;

					Iterator<SameSizeFiles>	sames_it = sames_copy.iterator();

					while( sames_it.hasNext()){

						SameSizeFiles same = sames_it.next();

						if ( same.sameAs( set )){

							found = true;

							sames_it.remove();

							break;
						}
					}

					if ( !found ){

						sames.add( new SameSizeFiles( set ));
					}
				}

				for ( SameSizeFiles dead: sames_copy ){

					dead.destroy();

					sames.remove( dead );
				}

				if ( enabled ){
					
					log( "Scan result: dm_map=" + dm_map.size() + ", sames=" + sames.size());
				}
				
				sames_dms.clear();
				
				if ( sames.size() > 0 ){

					for ( SameSizeFiles same: sames ){
						
						sames_dms.addAll( same.getDownloadManagers());
					}
					
					if ( timer_event == null ){

						timer_event =
							SimpleTimer.addPeriodicEvent(
								"GMFM:sync",
								TIMER_PERIOD,
								new TimerEventPerformer()
								{
									private int	tick_count = 0;

									@Override
									public void
									perform(
										TimerEvent event)
									{
										tick_count++;

										synchronized( dm_map ){

											for ( SameSizeFiles s: sames ){

												s.sync( tick_count );
											}
										}
									}
								});
					}
				}else{
					
					if ( timer_event != null ){

						timer_event.cancel();

						timer_event = null;
					}
				}
			}
		}
	}

	private class
	SameSizeFiles
	{
		final private Set<DiskManagerFileInfo>		files;
		final private List <SameSizeFileWrapper>	file_wrappers;

		final private Set<DownloadManager>			dm_set = new IdentityHashSet<>();

		private boolean	completion_logged;

		private volatile boolean	dl_has_restarted;

		private volatile boolean	destroyed;

		private String	abandon_reason;
		
		SameSizeFiles(
			Set<DiskManagerFileInfo>		_files )
		{
			files 	= _files;

			file_wrappers = new ArrayList<>( files.size());

				// make sure we init things before we start adding download listeners as they can 
				// callback during the process and traverse file_wrappers (for example)
			
			int	num = 0;
			
			for ( DiskManagerFileInfo file: files ){

				SameSizeFileWrapper file_wrapper = new SameSizeFileWrapper( num++, file );

				file_wrappers.add( file_wrapper );

				dm_set.add( file_wrapper.getDownloadManager());
			}
			
			for ( SameSizeFileWrapper file_wrapper: file_wrappers ){
				
				DiskManagerFileInfo file = file_wrapper.getFile();
				
				DownloadManager dm = file_wrapper.getDownloadManager();
				
				DownloadManagerPeerListenerEx dmpl =
					new DownloadManagerPeerListenerEx(){

						final Object		lock = this;

						DiskManager	current_disk_manager;

						boolean	pm_removed;

						final DiskManagerFileInfoListener file_listener =
							new DiskManagerFileInfoListener() {

								@Override
								public void
								dataWritten(
									long 	offset,
									long 	length,
									Object	originator )
								{
									if ( destroyed ){

										file.removeListener( this );

										return;
									}
									
									if ( originator instanceof String && ((String)originator).startsWith( ORIGINATOR_PREFIX )){
									
											// we originated this write, ignore
										
										return;
									}
									
									file_wrapper.dataWritten( offset, length, originator );
								}

								@Override
								public void
								dataChecked(
									long offset,
									long length )
								{
									if ( destroyed ){

										file.removeListener( this );

										return;
									}
								}
							};

						@Override
						public void
						sync()
						{
							if ( destroyed ){

								return;
							}

							sync_dispatcher.dispatch(
								new AERunnable()
								{
									@Override
									public void
									runSupport()
									{
										if ( destroyed ){

											return;
										}
										
										synchronized( lock ){

											if ( current_disk_manager != null ){

												file.removeListener( file_listener );

											}else{

												return;
											}
										}

										file.addListener( file_listener );
									}
								});
						}

						@Override
						public void
						peerManagerAdded(
							final PEPeerManager manager )
						{
							if ( destroyed ){

								return;
							}

							sync_dispatcher.dispatch(
								new AERunnable()
								{
									@Override
									public void
									runSupport()
									{
										if ( destroyed ){

											return;
										}

										if ( pm_removed ){

											log( dm.getDisplayName() + " restarted ");
											
											dl_has_restarted = true;
										}

										manager.addListener(
											new PEPeerManagerListenerAdapter(){

												@Override
												public void
												pieceCorrupted(
													PEPeerManager 	manager,
													int 			piece_number )
												{
													if ( destroyed ){

														manager.removeListener( this );

														return;
													}

													file_wrapper.pieceCorrupt( piece_number );
												}
											});

										synchronized( lock ){

											if ( current_disk_manager != null ){

												file.removeListener( file_listener );
											}

											current_disk_manager = manager.getDiskManager();

											if ( current_disk_manager == null ){

												return;
											}
										}

										file.addListener( file_listener );
									}
								});
						}

						@Override
						public void
						peerManagerRemoved(
							PEPeerManager manager)
						{
							sync_dispatcher.dispatch(
								new AERunnable()
								{
									@Override
									public void
									runSupport()
									{
										synchronized( lock ){

											pm_removed = true;

											if ( current_disk_manager != null ){

												file.removeListener( file_listener );

												current_disk_manager = null;
											}
										}
									}
								});
						}

						@Override
						public void
						peerAdded(
							PEPeer peer )
						{
						}

						@Override
						public void
						peerRemoved(
							PEPeer peer )
						{
						}

						@Override
						public void
						peerManagerWillBeAdded(
							PEPeerManager manager )
						{
						}
					};

				dm.setUserData( SameSizeFiles.this, dmpl );

				dm.addPeerListener( dmpl );
			}

			dl_has_restarted = true;
			
			if ( !logging_paused ){
				
				log( "Created" );
				
				logCurrentState();
			}
		}

		boolean
		hasDownloadManager(
			DownloadManager	dm )
		{
			return( dm_set.contains( dm ));
		}

		Set<DownloadManager>
		getDownloadManagers()
		{
			return( dm_set );
		}
		
		void
		sync(
			int		tick_count )
		{
			if ( read_write_dispatcher.getQueueSize() > 0 ){

					// stuff is going on, ignore sync until things are idle

				return;
			}

			boolean	do_sync 	= tick_count % SYNC_TIMER_TICKS == 0;
			boolean	do_force 	= tick_count % FORCE_PIECE_TIMER_TICKS == 0;

			if ( dl_has_restarted ){

				dl_has_restarted = false;

				do_sync = true;
			}

			if ( !( do_sync || do_force )){

				return;
			}

			Set<DiskManagerFileInfo>	active = new HashSet<>();

			int		num_incomplete	= 0;

			for ( DiskManagerFileInfo file: files ){

				if ( file.isSkipped()){

					continue;
				}

				int dl_state = file.getDownloadManager().getState();

				if ( dl_state == DownloadManager.STATE_DOWNLOADING || dl_state ==  DownloadManager.STATE_SEEDING ){

					active.add( file );

					if ( file.getLength() != file.getDownloaded()){

						num_incomplete++;
					}
				}
			}

			if ( num_incomplete > 0 &&  active.size() > 1 ){

				boolean rta_active = false;

				for ( DiskManagerFileInfo file: active ){

					DownloadManager	dm = file.getDownloadManager();

					if ( do_sync ){

						DownloadManagerPeerListenerEx dmpl = (DownloadManagerPeerListenerEx)dm.getUserData( SameSizeFiles.this );

						if ( dmpl != null ){

							dmpl.sync();
						}
					}

					PEPeerManager pm = dm.getPeerManager();

					if ( pm != null ){

						if ( pm.getPiecePicker().getRTAProviders().size() > 0 ){

							rta_active = true;
						}
					}
				}

				if ( rta_active ){

					do_force = false;
				}

				if ( do_force ){

					try{
							// see if we can force some pieces in one file for a missing piece in another
							// but only one piece at a time to avoid messing with things too much

						for ( SameSizeFileWrapper ss_file: file_wrappers ){

							DiskManagerFileInfo file = ss_file.getFile();

							if ( active.contains( file )){

								DiskManager 	dm = ss_file.getDiskManager();
								PEPeerManager 	pm = ss_file.getPeerManager();

								if ( dm == null || pm == null ){

									continue;
								}

								DiskManagerPiece[] pieces = dm.getPieces();

								int	first_piece = file.getFirstPieceNumber();
								int	last_piece	= file.getLastPieceNumber();

								long	file_length = file.getLength();

								long	piece_size 	= dm.getPieceLength();

								long file_start_offset = ss_file.getFileByteOffset();

								boolean	force_done = false;

								int [] availability = pm.getAvailability();

								for ( int i=first_piece; i<=last_piece && !force_done; i++ ){

									DiskManagerPiece piece = pieces[i];

									if ( piece.isInteresting() && availability[i] == 0 ){

										long start_in_file 			= piece_size*i - file_start_offset;
										long end_in_file_exclusive	= start_in_file + piece.getLength();

										if ( start_in_file < 0 ){

											start_in_file = 0;

										}

										if ( end_in_file_exclusive > file_length ){

											end_in_file_exclusive = file_length;
										}

										for ( SameSizeFileWrapper o_ss_file: file_wrappers ){

											if ( ss_file == o_ss_file || !active.contains( o_ss_file.getFile())){

												continue;
											}

											if ( o_ss_file.forceRange( i, start_in_file, end_in_file_exclusive )){

												force_done = true;

												break;
											}
										}
									}
								}
							}
						}
					}catch( Throwable e ){

						Debug.out( e );
					}
				}
			}

			if ( !do_sync ){

				return;
			}

			if ( !completion_logged ){

				boolean	all_done 		= true;
				long	total_merged	= 0;

				for ( SameSizeFileWrapper ssf: file_wrappers ){

					if ( ssf.isSkipped()){

						continue;
					}

					total_merged += ssf.getMergedByteCount();

					if ( !ssf.isComplete()){

						all_done = false;
					}
				}

				if ( all_done ){

					completion_logged	= true;

					if ( total_merged > 0 ){

						String msg = "Successfully merged files:\n";

						for ( SameSizeFileWrapper file: file_wrappers ){

							long	merged = file.getMergedByteCount();

							if ( merged > 0 ){

								DownloadManager dm = file.getDownloadManager();

								msg += dm.getDisplayName();

								if ( !dm.getTorrent().isSimpleTorrent()){

									msg += " - " + file.getFile().getTorrentFile().getRelativePath();
								}

								msg +=  ": " + DisplayFormatters.formatByteCountToKiBEtc( merged ) + "\n";
							}
						}

						msg += "\nTotal: " + DisplayFormatters.formatByteCountToKiBEtc( total_merged );

						log( msg );
						
						Logger.log(
								new LogAlert(
									true,
									LogAlert.AT_INFORMATION,
									msg ));
					}
				}
			}
		}

		boolean
		sameAs(
			Set<DiskManagerFileInfo>		_others )
		{
			return( files.equals( _others ));
		}

		void
		abandon(
			SameSizeFileWrapper		failed )
		{
			destroy();

			String msg = "Abandoned attempt to merge files:\n";

			for ( SameSizeFileWrapper file: file_wrappers ){

				msg += file.getDownloadManager().getDisplayName() + " - " + file.getFile().getTorrentFile().getRelativePath() + "\n";
			}
			msg += "\nToo many hash fails in " + failed.getDownloadManager().getDisplayName();

			abandon_reason = msg;
			
			Logger.log( new LogEvent( LogIDs.CORE, msg ));

			log( msg );
		}

		String
		getInfo()
		{
			StringBuilder msg = new StringBuilder(1024);

			long	size_min = -1;
			long	size_max = -1;

			for ( SameSizeFileWrapper file: file_wrappers ){

				DiskManagerFileInfo f = file.getFile();

				if ( size_min == -1 ){

					size_min = size_max = f.getLength();
				}else{
					
					if ( tolerance != 0 ){
						
						long l2 = f.getLength();
						
						size_min = Math.min( size_min, l2 );
						size_max = Math.max( size_max, l2 );
					}
				}

				msg.append( "  " );
				msg.append( file.getDownloadManager().getDisplayName());
				msg.append( ": " );
				msg.append( f.getTorrentFile().getRelativePath());
				msg.append( "\n" );
				
				msg.append( "    " + file.getInfo());
				msg.append( "\n" );
			}

			String size_str = DisplayFormatters.formatByteCountToKiBEtc( size_min );
			
			if ( size_max > size_min ){
				
				size_str += " (+" + (size_max-size_min ) + ")";
			}
			
			return( "Size: " + size_str + "\n" + msg.toString());
		}

		void
		destroy()
		{
			destroyed = true;

			for ( DiskManagerFileInfo file: files ){

				DownloadManager	dm = file.getDownloadManager();

				DownloadManagerPeerListenerEx dmpl = (DownloadManagerPeerListenerEx)dm.getUserData( SameSizeFiles.this );

				if ( dmpl != null ){

					dm.setUserData( SameSizeFiles.this, null );
					
					dm.removePeerListener( dmpl );
				}
			}
		}

		private void
		logCurrentState()
		{
			logCurrentState( null );
		}
		
		private void
		logCurrentState(
			IndentWriter	writer )
		{
			long	size_min = -1;
			long	size_max = -1;

			for ( SameSizeFileWrapper file: file_wrappers ){

				DiskManagerFileInfo f = file.getFile();

				if ( size_min == -1 ){

					size_min = size_max = f.getLength();
				}else{
					
					if ( tolerance != 0 ){
						
						long l2 = f.getLength();
						
						size_min = Math.min( size_min, l2 );
						size_max = Math.max( size_max, l2 );
					}
				}
			}

			String size_str = DisplayFormatters.formatByteCountToKiBEtc( size_min );
			
			if ( size_max > size_min ){
				
				size_str += " (+" + (size_max-size_min ) + ")";
			}
			
			log( writer, "  " + size_str + ", files=" + file_wrappers.size());
			
			if ( completion_logged ){
				
				log( writer, "    Completed" );
				
			}else if ( abandon_reason != null ){
				
				log( writer, "    Abandoned: " + abandon_reason );
				
			}else{
				
				for ( SameSizeFileWrapper file: file_wrappers ){
					
					file.logCurrentState( writer );
				}
			}
		}
		private String
		getString()
		{
			String str = "";

			long	size = -1;

			for ( DiskManagerFileInfo file: files ){

				size = file.getLength();

				str += (str.length()==0?"":", ") + file.getTorrentFile().getRelativePath();
			}

			str += " - length " + size;

			return( str );
		}


		private class
		SameSizeFileWrapper
		{
			private final DownloadManager			download_manager;

			private final int						wrapper_num;
			private final DiskManagerFileInfo		file;

			private final long						piece_length;	// needs to be long for overflow purposes
			private final long						file_length;
			private final long						file_byte_offset;
			
			private final int						first_piece_number;
			private final int						last_piece_number;
			private final int						first_piece_block_number;
			private final int						last_piece_block_number;
						
			private final String					id;
			
			private long							merged_byte_counnt;

			private final boolean[]					modified_pieces;

			private int	pieces_completed;
			private int	pieces_corrupted;
			private int hash_fails_allowed	= HASH_FAILS_BEFORE_QUIT;
			
			private int	forced_start_piece		= 0;
			private int forced_end_piece		= -1;

			SameSizeFileWrapper(
				int						_wrapper_num,
				DiskManagerFileInfo		_file )
			{
				wrapper_num		= _wrapper_num;
				file			= _file;

				modified_pieces	= new boolean[ file.getNbPieces()];

				download_manager = file.getDownloadManager();

				file_length = file.getLength();
				
				int	file_index = file.getIndex();

				long fbo = 0;

				if ( file_index > 0){

					DiskManagerFileInfo[] f = download_manager.getDiskManagerFileInfoSet().getFiles();

					for ( int i=0;i<file_index;i++ ){

						fbo += f[i].getLength();
					}
				}

				TOTorrent torrent =  download_manager.getTorrent();
						
				String _id;

				try{
					_id = Base32.encode( torrent.getHash()) + "/" + file.getIndex();

				}catch( Throwable e ){

					_id = download_manager.getDisplayName() + "/" + file.getIndex();
				}

				id	= _id;

				file_byte_offset = fbo;
				
				piece_length = torrent.getPieceLength();
				
				first_piece_number 	= file.getFirstPieceNumber();
				last_piece_number 	= file.getLastPieceNumber();
								
				first_piece_block_number = (int)( file_byte_offset%piece_length)/DiskManager.BLOCK_SIZE;
				
				long file_end_offset = file_byte_offset + file_length;
				
				int	end_rem = (int)( file_end_offset%piece_length);

				if ( end_rem == 0 ){
					end_rem = (int)(piece_length);	// exact piece so last block is last block
				}
				
				last_piece_block_number	= (end_rem-1)/DiskManager.BLOCK_SIZE;
			}

			DiskManagerFileInfo
			getFile()
			{
				return( file );
			}

			boolean
			isSkipped()
			{
				return( file.isSkipped());
			}

			boolean
			isComplete()
			{
				return( file.getLength() == file.getDownloaded());
			}

			DownloadManager
			getDownloadManager()
			{
				return( download_manager );
			}

			DiskManager
			getDiskManager()
			{
				return( file.getDiskManager());
			}

			PEPeerManager
			getPeerManager()
			{
				return( download_manager.getPeerManager());
			}

			long
			getFileByteOffset()
			{
				return( file_byte_offset );
			}
			
			private String
			getID()
			{
				return( id );
			}

			void
			dataWritten(
				long 		initial_file_write_offset,
				long 		initial_file_write_length,
				Object		originator )
			{
				final byte SS = DirectByteBuffer.SS_EXTERNAL;
				
				DiskManager			disk_manager	= getDiskManager();
				PEPeerManager		peer_manager 	= getPeerManager();

				if ( disk_manager == null || peer_manager == null ){

					return;
				}
				
				DiskManagerPiece[]	origin_pieces 			= disk_manager.getPieces();
				long 				origin_piece_length 	= piece_length;

				for ( SameSizeFileWrapper target_file: file_wrappers ){

					if ( target_file == this || target_file.isSkipped() || target_file.isComplete()){

						continue;
					}

					final DiskManager 		target_disk_manager = target_file.getDiskManager();
					final PEPeerManager 	target_peer_manager = target_file.getPeerManager();

					if ( target_disk_manager == null || target_peer_manager == null ){

						continue;
					}

					read_write_dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								if ( target_file.isComplete()){

									return;
								}
								
								if ( !logging_paused ){
								
									logFile( "data write: " + SameSizeFileWrapper.this.getName() + "->" + target_file.getName() + ": offset=" + initial_file_write_offset + ", len=" + initial_file_write_length );
								}

								DiskManagerPiece[]	target_pieces = target_disk_manager.getPieces();
																
								long target_piece_length 	= target_file.piece_length;

								long	file_write_offset = initial_file_write_offset;
								long	file_write_length = initial_file_write_length;
									
									// there's some bug whereby we occasionally get here with the length being too large
									// work around it
								
								if ( file_write_offset + initial_file_write_length > file_length ){
									
									file_write_length = file_length - file_write_offset;
									
									if ( file_write_length <= 0 ){
										
										return;
									}
								}
								
									// figure out origin details
								
								long	origin_written_start 				= file_byte_offset + file_write_offset;
								long	origin_written_end_inclusive		= origin_written_start + file_write_length - 1;
				
								int	origin_first_piece_num 	= (int)( origin_written_start/origin_piece_length );
								int	origin_last_piece_num 	= (int)( origin_written_end_inclusive/origin_piece_length );
									
								int	origin_first_block 	= (int)( origin_written_start % origin_piece_length )/DiskManager.BLOCK_SIZE;
								int	origin_last_block 	= (int)( origin_written_end_inclusive % origin_piece_length )/DiskManager.BLOCK_SIZE;

								int origin_block_offset = (int)( origin_written_start % DiskManager.BLOCK_SIZE );
								
									// now see if we can widen this by a block each way to handle non-aligned blocks
								
								if ( origin_first_piece_num != first_piece_number || origin_first_block != first_piece_block_number ){
								
									int prev_block_num = origin_first_block -1;
									
									int prev_piece_num = origin_first_piece_num;
									
									DiskManagerPiece prev_piece = origin_pieces[origin_first_piece_num];
																		
									if ( prev_block_num < 0 ){
										
										prev_piece_num--;
										
										prev_piece = origin_pieces[prev_piece_num];
										
										prev_block_num = prev_piece.getNbBlocks()-1;
									}
									
									if ( prev_piece.isWritten( prev_block_num )){
										
										int	added_size = prev_piece.getBlockSize( prev_block_num );
										
										file_write_offset 	-= added_size;
										file_write_length	+= added_size;
										
										origin_first_piece_num 	= prev_piece_num;
										origin_first_block		= prev_block_num;
									}
								}
								
								if ( origin_last_piece_num != last_piece_number || origin_last_block != last_piece_block_number ){
									
									int next_block_num = origin_last_block +1;
										
									int next_piece_num = origin_last_piece_num;

									DiskManagerPiece next_piece = origin_pieces[origin_last_piece_num];
									
									if ( next_piece.getNbBlocks() <= next_block_num ){
										
										next_piece_num++;
										
										next_piece = origin_pieces[next_piece_num];

										next_block_num = 0;
									}
																	
									if ( next_piece.isWritten( next_block_num )){
										
										int	added_size = next_piece.getBlockSize( next_block_num );
										
										file_write_length	+= added_size;
										
										origin_last_piece_num 	= next_piece_num;
										origin_last_block		= next_block_num;
									}
								}
								
								origin_written_start 				= file_byte_offset + file_write_offset;
								origin_written_end_inclusive		= origin_written_start + file_write_length - 1;

								
								// System.out.println( "Data written to " + file.getFile(true).getAbsolutePath() + ": offset=" + file_write_offset + ", len=" + file_write_length + " -> " + origin_first_piece_num + "/" + origin_first_block + "/" + origin_block_offset );
								
									// figure out target details
								
								long	target_written_start 				= target_file.file_byte_offset + file_write_offset;
								long	target_written_end_inclusive		= target_written_start + file_write_length - 1;
				
								int	target_first_piece_num 	= (int)( target_written_start/target_piece_length );
								int	target_last_piece_num 	= (int)( target_written_end_inclusive/target_piece_length );
				
				
								int	target_first_block 	= (int)( target_written_start % target_piece_length )/DiskManager.BLOCK_SIZE;
								int	target_last_block 	= (int)( target_written_end_inclusive % target_piece_length )/DiskManager.BLOCK_SIZE;

								int target_block_offset = (int)( target_written_start % DiskManager.BLOCK_SIZE );

									// setup iterators
								
								int read_piece_num 		= origin_first_piece_num;
								int read_block_num		= origin_first_block;
								int read_block_offset	= origin_block_offset;
								
								DiskManagerPiece	read_piece 	= origin_pieces[read_piece_num];
								
								int write_piece_num 	= target_first_piece_num;
								int write_block_num		= target_first_block;
								int write_block_offset	= target_block_offset;
								
								DiskManagerPiece	write_piece 	= target_pieces[write_piece_num];

								boolean read_complete = false;
								boolean write_complete = false;
								
								DirectByteBuffer read_block = null;
								DirectByteBuffer write_block = null;
								
								try{
									int		consecutive_skips = 0;
									
									while( !write_complete ){
											
										if ( destroyed ){

											break;
										}

										boolean check_written = true;
										
										boolean skip_block = write_piece.isDone();
										
										if ( !skip_block ){
											
												// allow over-writing for partial blocks
											
											if (	( write_piece_num == target_file.first_piece_number && write_block_num == target_file.first_piece_block_number ) ||
													( write_piece_num == target_file.last_piece_number && write_block_num == target_file.last_piece_block_number )){
												
												check_written = false;
												
											}else{
																								
												boolean[] written = write_piece.getWritten();
												
												if ( written != null ){
													
													if ( written[ write_block_num ] ){
														
														skip_block = true;
													}
												}
											}
										}
										
										// skip_block = RandomUtils.nextInt( 2 ) == 1;
										
										if ( skip_block ){
												
											consecutive_skips++;
											
											if ( consecutive_skips > 1 ){
												
													// read offset already set as required, just bump block
												
												read_block_num++;
																									
											}else{
												
												int	read_ahead = -1;
												
												if ( read_block != null ){
													
													read_ahead = read_block.remaining( SS );
													
													read_block.returnToPool();
													
													read_block = null;
												}
												
												int	relative_block_offset = origin_block_offset - target_block_offset;
												
												if ( relative_block_offset == 0 ){
													
														// no skew, simply advance to next block with no offsets
													
													read_block_offset = 0;
													
													read_block_num++;
													
												}else if ( read_ahead > 0 ){
													
														// already processed a block or two, we can use the size of the look-ahead to figure out what read offset
														// is required to compensate for subsequent blocks
													
													read_block_offset = DiskManager.BLOCK_SIZE - read_ahead;
													
												}else{
													
														// should only get here when skipping the very first block of the sequence
													
													//double check this
													
													if ( relative_block_offset < 0 ){
																													
														read_block_offset	= read_piece.getBlockSize( read_block_num ) + relative_block_offset;
													
													}else{
														
														read_block_offset = relative_block_offset;
														
														read_block_num++;									
													}
												}
											}												

											if ( read_piece_num == origin_last_piece_num && read_block_num > origin_last_block ){
											
												break;
												
											}else{
												
												if ( read_block_num == read_piece.getNbBlocks()){
													
													read_piece_num++;
												
													read_piece 	= origin_pieces[read_piece_num];
												
													read_block_num = 0;
												}
											}
											
											write_block_num++;
											write_block_offset = 0;

											if ( write_piece_num == target_last_piece_num && write_block_num > target_last_block ){
												
												break;
												
											}else{
											
												if ( write_block_num == write_piece.getNbBlocks()){
													
													write_piece_num++;
													
													write_piece 	= target_pieces[write_piece_num];
													
													write_block_num = 0;
												}
											}
											
											continue;
										}
										
										consecutive_skips = 0;
										
										if ( write_block != null ){
											
											Debug.out( "eh?" );
											
											write_block.returnToPool();
											
											write_block = null;
										}
										
										write_block = DirectByteBufferPool.getBuffer( DirectByteBuffer.AL_EXTERNAL, write_piece.getBlockSize( write_block_num ) );
	
										int block_offset = write_block_offset;
										
										if ( block_offset > 0 ){
											
											write_block.position( SS, write_block.position( SS ) + block_offset );
											
											write_block_offset = 0;		// only needed at start of first block
										}
										
										while( write_block.hasRemaining( SS )){
											
											if ( destroyed ){

												break;
											}

											if ( read_block == null ){
												
												if ( read_complete ){
													
													break;
												}											
													
												read_block = disk_manager.readBlock( read_piece_num , read_block_num*DiskManager.BLOCK_SIZE, read_piece.getBlockSize( read_block_num ));									
												
												if ( read_block == null ){
													
													break;
												}
												
												if ( read_piece_num == origin_last_piece_num && read_block_num == origin_last_block ){
													
													read_complete = true;
													
												}else{
												
													read_block_num++;
												
													if ( read_block_num == read_piece.getNbBlocks()){
													
														read_piece_num++;
														
														read_piece 	= origin_pieces[read_piece_num];
														
														read_block_num = 0;
													}
												}
																						
												if ( read_block_offset > 0 ){
													
													read_block.position( SS, read_block.position( SS ) + read_block_offset );
													
													read_block_offset = 0;	// only needed at start of first block or after skipping blocks
												}
												
												read_piece.setMergeRead();	
											}
										
											int	write_space = write_block.remaining( SS );
										
											int read_remaining = read_block.remaining( SS );
										
											int read_limit = read_block.limit( SS );
											
											if ( write_space < read_remaining ){
											
												read_block.limit( SS, read_block.position( SS ) + write_space );
											}
										
											write_block.put( SS, read_block );
											
											read_block.limit( SS, read_limit );
											
											if ( !read_block.hasRemaining( SS )){
												
												read_block.returnToPool();
												
												read_block = null;
											}
										}
											
										boolean skip_incomplete = false;
										
										if ( block_offset > 0 || write_block.hasRemaining( SS )){
											
												// block has not been completely filled. Only allow this to be written if it is an edge block
											
											if (	( write_piece_num == target_file.first_piece_number && write_block_num == target_file.first_piece_block_number ) ||
													( write_piece_num == target_file.last_piece_number && write_block_num == target_file.last_piece_block_number )){
												
												// ok, let it through
												
											}else{
												
												skip_incomplete = true;
											}
										}
										
										write_block.position( SS, 0 );		// don't use 'flip' as we always want to provide the entire block to writeBlock to 
																			// allow it to fill in the missing data for a partial block
										
										boolean[] written = write_piece.getWritten();
										
										if ( skip_incomplete || write_piece.isDone() || ( check_written && written != null && written[ write_block_num ])){
											
											write_block.returnToPool();
											
											write_block = null;

										}else{
											
											if ( !logging_paused ){
												
												logFile( "Write from " + read_piece_num + "/" + read_block_num + " to " + write_piece_num + "/" + write_block_num );
											}
												
											if ( target_file.writeBlock( write_piece, write_piece_num, write_block_num, write_block, block_offset )){
											
												write_block = null;

												write_piece.setMergeWrite();
												
												if ( write_piece.isWritten()){

													pieces_completed++;

													if ( pieces_completed < 5 ){

														try{
															Thread.sleep(500);

														}catch( Throwable e ){

														}
													}
												}
											}else{
			
												write_block.returnToPool();
												
												write_block = null;
												
												break;
											}
										}
																															
										if ( write_piece_num == target_last_piece_num && write_block_num == target_last_block ){
											
											write_complete = true;
											
										}else{
											
											write_block_num++;
											
											if ( write_block_num == write_piece.getNbBlocks()){
											
												write_piece_num++;
												
												write_piece 	= target_pieces[write_piece_num];
												
												write_block_num = 0;
											}
										}
									}
								}finally{
									
									if ( read_block != null ){
									
										read_block.returnToPool();
									}
									
									if ( write_block != null ){
										
										write_block.returnToPool();
									}
								}
							}
						});
				}
			}

			boolean
			writeBlock(
				DiskManagerPiece	piece,
				int					piece_number,
				int					block_number,
				DirectByteBuffer	buffer,
				int					block_offset )
			{
				PEPeerManager 	pm = getPeerManager();
				DiskManager	 	dm = getDiskManager();

				if ( pm == null || dm == null ){

					return( false );
				}

				byte SS = DirectByteBuffer.SS_EXTERNAL;
				
				int	length = buffer.remaining( SS );
				
				long write_start 	= piece_number * piece_length + block_number * DiskManager.BLOCK_SIZE;
				long write_end		= write_start + length;
				
				int block_size = piece.getBlockSize( block_number );
				
				DirectByteBuffer existing	= null;
				
				try{
					if ( write_start < file_byte_offset ){
											
						try{
							int	before = (int)( file_byte_offset - write_start );
																		
							existing = dm.readBlock( piece_number , block_number*DiskManager.BLOCK_SIZE, block_size );
		
							if ( existing != null ){
								
								existing.limit( SS, before );
								
								int old_pos = buffer.position( SS );
													
								buffer.put( SS, existing );
								
								buffer.position( SS, old_pos );
								
								existing.position( SS, 0 );
								
								existing.limit( SS, block_size );
								
							}else{
								
								return( false );
							}
							
						}catch( Throwable e ){
							
							Debug.out( e );
							
							return( false );
						}
					}
					
					if ( write_end > file_byte_offset + file_length ){
												
						try{
							int	after = (int)( write_end - ( file_byte_offset + file_length ));
								
							if ( existing == null ){
							
								existing = dm.readBlock( piece_number , block_number*DiskManager.BLOCK_SIZE, block_size );
							}
							
							if ( existing != null ){
								
								existing.position( SS, block_size - after );
								
								int old_pos = buffer.position( SS );
								
								buffer.position( SS, block_size - after );
								
								int rem = buffer.remaining( SS );
								
								if ( existing.remaining( SS ) > rem ){
									
									existing.limit( SS, rem );
								}
								
								buffer.put( SS, existing );
								
								buffer.position( SS, old_pos );
								
								existing.position( SS, 0 );
								
								existing.limit( SS, block_size );
								
							}else{
								
								return( false );
							}
							
						}catch( Throwable e ){
							
							Debug.out( e );
							
							return( false );
						}
					}				

					if ( existing != null ){				
						
						if ( hash_fails_allowed < 16 ){
						
							hash_fails_allowed++;	// most likely will cause a hash fail but we want to live with this as expected as partial write
						}
					}
				
				}finally{
					
					if ( existing != null ){
						
						existing.returnToPool();
					}
				}

				int file_piece_num = piece_number - file.getFirstPieceNumber();

				modified_pieces[ file_piece_num ] = true;
				
				synchronized( merged_data_lock ){

					DownloadManagerState dms = download_manager.getDownloadState();

					long merged = dms.getLongAttribute( DownloadManagerState.AT_MERGED_DATA );

					merged += length;

					dms.setLongAttribute( DownloadManagerState.AT_MERGED_DATA, merged );
				}

				merged_byte_counnt += length;

				piece.clearWritten( block_number );
				
				pm.writeBlock( piece_number, block_number*DiskManager.BLOCK_SIZE, buffer, ORIGINATOR_PREFIX + " from " + getID() + "[" + piece_number + "/" + block_number + "/" + length + "]", true );

				return( true );
			}

			void
			pieceCorrupt(
				int		piece_number )
			{
				int	first_piece = file.getFirstPieceNumber();

				if ( piece_number >= first_piece && piece_number <= file.getLastPieceNumber()){

					if ( modified_pieces[ piece_number - first_piece ] ){

						pieces_corrupted++;

						logFile( "piece " + piece_number + " corrupt, bad=" + pieces_corrupted + ", good=" + pieces_completed );
						
						if ( pieces_corrupted >= hash_fails_allowed ){

							abandon( this );
						}
					}
				}
			}

			boolean
			forceRange(
				int		for_piece,
				long	start_in_file,
				long	end_in_file_exclusive )
			{
				DiskManager 	dm = getDiskManager();
				PEPeerManager 	pm = getPeerManager();

				if ( dm == null || pm == null ){

					return( false );
				}

				int[] availability = pm.getAvailability();

				long	start_in_torrent 			= start_in_file + file_byte_offset;
				long	end_in_torrent_inclusive	= end_in_file_exclusive + file_byte_offset - 1;

				int	piece_size = dm.getPieceLength();

				int	first_piece = (int)(start_in_torrent/piece_size);
				int	last_piece	= (int)(end_in_torrent_inclusive/piece_size);

				DiskManagerPiece[] pieces = dm.getPieces();

				boolean	forceable = false;

				for ( int i=first_piece; i<=last_piece;i++ ){

					DiskManagerPiece piece = pieces[i];

					if ( !piece.isDone()){

						if ( 	availability[ piece.getPieceNumber()] > 0 &&
								piece.isInteresting()){

							forceable = true;

							break;
						}
					}
				}

				if ( forceable ){

					if ( forced_start_piece == first_piece && forced_end_piece == last_piece ){

						// nothing changed

					}else{

						PiecePicker pp = pm.getPiecePicker();

						if ( forced_start_piece != first_piece || forced_end_piece != last_piece ){

							for ( int i=forced_start_piece; i<=forced_end_piece;i++ ){

								DiskManagerPiece piece = pieces[i];

								pp.setForcePiece( piece.getPieceNumber(), false );
							}
						}

						forced_start_piece 	= first_piece;
						forced_end_piece	= last_piece;

						for ( int i=first_piece; i<=last_piece;i++ ){

							DiskManagerPiece piece = pieces[i];

							if ( !piece.isDone()){

								pp.setForcePiece( i, true );
							}
						}

						if ( !logging_paused ){
						
							logFile( "Forced pieces for " + for_piece + ": " + forced_start_piece + " -> " + forced_end_piece + " in " + download_manager.getDisplayName() + "/" + file.getTorrentFile().getRelativePath()); 
						}
					}

					return( true );

				}else{

					return( false );
				}
			}
			
			long
			getMergedByteCount()
			{
				return( merged_byte_counnt );
			}

			String
			getInfo()
			{
				return( 
					"merged=" + DisplayFormatters.formatByteCountToKiBEtc( merged_byte_counnt ) +
					", ok=" + pieces_completed + 
					", bad=" + pieces_corrupted + 
					", force=[" + (forced_end_piece==-1?"":(forced_start_piece + "-" + forced_end_piece)) + "]" );
			}
			
			private String
			getName()
			{
				return( file.getFile(true).getName() + "[" + wrapper_num + "]" );
			}
			
			private void
			logFile(
				String		str )
			{
				log( getName() + ": " + str );
			}
			
			private void
			logCurrentState(
				IndentWriter	writer )
			{
				log( writer, "      " + (writer==null?getName():Debug.secretFileName(getName())) + ": " + getInfo());
			}
		}
	}

	private void
	logCurrentState(
		IndentWriter	write )
	{
		synchronized( dm_map ){

			log( write, "Same size file sets=" + sames.size() + ", dm_map=" + dm_map.size());
			
			for ( SameSizeFiles same: sames ){
				
				same.logCurrentState( write );
			}
		}
	}
	
	@Override
	public void
	generate(
		IndentWriter writer )
	{
		writer.println( "Swarm Merge" );

		try{
			writer.indent();

			logCurrentState( writer );
			
		}finally{

			writer.exdent();
		}
	}
	
	private interface
	DownloadManagerPeerListenerEx
		extends DownloadManagerPeerListener
	{
		public void
		sync();
	}
}
