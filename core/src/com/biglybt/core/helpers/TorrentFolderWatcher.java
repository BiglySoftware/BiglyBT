/*
 * Created on May 25, 2004
 * Created by Alon Rohter
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

package com.biglybt.core.helpers;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ConfigKeys;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerFileInfoSet;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerInitialisationAdapter;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagFeatureFileLocation;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.util.*;
import com.biglybt.core.util.protocol.magnet.MagnetConnection2;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.logging.LoggerChannel;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.model.BasicPluginViewModel;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;

/**
 * Watches a folder for new torrents and imports them.
 * NOTE: Folder-to-watch and other watching params are taken from a global
 *       config option right now, so starting multiple instances of
 *       TorrentFolderWatcher is useless as currently coded.
 */
public class TorrentFolderWatcher {
	private final static LogIDs LOGID = LogIDs.CORE;

	private final static String PARAMID_FOLDER = "Watch Torrent Folder";

	private volatile GlobalManager _global_manager;

	private final LoggerChannel log;
	
	volatile boolean running = false;

	private final ArrayList<TOTorrent> to_delete = new ArrayList<>();

	protected final AEMonitor this_mon = new AEMonitor("TorrentFolderWatcher");

	private final FilenameFilter filename_filter = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String name) {
			String lc_name = name.toLowerCase();

			return ( lc_name.endsWith(".torrent") || lc_name.endsWith(".tor") || lc_name.endsWith(".magnet"));
		}
	};

	private final ParameterListener param_listener = new ParameterListener() {
		@Override
		public void parameterChanged(String parameterName) {
			if (COConfigurationManager.getBooleanParameter(PARAMID_FOLDER)) {
				if (!running) {
					running = true;
					if ( !watch_thread.isAlive()){
							// crap code this but I can't be bothered to fix it
						watch_thread.setDaemon(true);
						watch_thread.setPriority(Thread.MIN_PRIORITY);
						watch_thread.start();
					}
				}
			} else{
				running = false;
			}
		}
	};

	final Thread watch_thread =
		new AEThread("FolderWatcher")
		{
			private long	last_run = 0;	// trigger a run on startup

			final AESemaphore wait_sem = new AESemaphore( "fw:wait" );

			{
				COConfigurationManager.addParameterListener(
					new String[]{
						PARAMID_FOLDER,
						"Watch Torrent Folder Interval Secs",
					},
					new ParameterListener()
					{
						@Override
						public void
						parameterChanged(
							String name )
						{
							wait_sem.release();
						}
					});
			}

			@Override
			public void
			runSupport()
			{
				while( true ){

					while( true ){

						long	now = SystemTime.getMonotonousTime();

						int sleep_secs = COConfigurationManager.getIntParameter("Watch Torrent Folder Interval Secs");

						if ( sleep_secs < 1 ){

							sleep_secs = 1;
						}

						int sleep_ms	= sleep_secs*1000;

						long	remaining = last_run + sleep_ms - now;

						if ( remaining < 250 || last_run == 0 ){

							last_run = now;

							break;
						}

						wait_sem.reserve( remaining );
					}

					try{
						if ( running ){

							importAddedFiles();

						}else{

							wait_sem.reserve(60*1000);
						}

					}catch( Throwable e){

						Debug.out( e );
					}
				}
			}
		};

	/**
	 * Start a folder watcher, which will auto-import torrents via the given
	 * manager.
	 *
	 * @param global_manager
	 */
	public TorrentFolderWatcher(GlobalManager global_manager) {
		_global_manager = global_manager;
		
		Core core = CoreFactory.getSingleton();
		PluginInterface plugin_interface = core.getPluginManager().getDefaultPluginInterface();

		log = plugin_interface.getLogger().getTimeStampedChannel( "Torrent Importer" );

		UIManager	ui_manager = plugin_interface.getUIManager();

		BasicPluginViewModel model = ui_manager.createBasicPluginViewModel( "log.torrent.importer" );

		model.getActivity().setVisible( false );
		model.getProgress().setVisible( false );

		model.attachLoggerChannel( log );
	}

	public void
	start()
	{
		if (COConfigurationManager.getBooleanParameter(PARAMID_FOLDER)) {
			running = true;
			watch_thread.setDaemon(true);
			watch_thread.setPriority(Thread.MIN_PRIORITY);
			watch_thread.start();
		}

		COConfigurationManager.addParameterListener(PARAMID_FOLDER, param_listener);
	}

	/**
	 * Stop and terminate this folder importer watcher.
	 */
	public void destroy() {
		running = false;
		_global_manager = null;
		COConfigurationManager.removeParameterListener(PARAMID_FOLDER,
				param_listener);
	}

	private File
	renameToImported(
		File 	parent,
		File	file )
	{
		File imported = FileUtil.newFile (parent, file.getName() + ".imported");
		
		if ( imported.exists()){
			
			imported.delete();
		}
		
		TorrentUtils.move(file, imported);
		
		return( imported );
	}
	
	void 
	importAddedFiles() 
	{
		Core core = CoreFactory.getSingleton();

		try {
			this_mon.enter();

			if (!running){
				return;
			}

			GlobalManager global_manager = _global_manager;

			if ( global_manager == null || !core.isStarted()){

				return;
			}

			com.biglybt.pif.download.DownloadManager plugin_dm = core.getPluginManager().getDefaultPluginInterface().getDownloadManager();

			boolean save_torrents_default = COConfigurationManager.getBooleanParameter("Save Torrent Files");

			String torrent_save_path = COConfigurationManager.getStringParameter("General_sDefaultTorrent_Directory");

			int start_mode = COConfigurationManager.getIntParameter( "Watch Torrents Add Mode" );

			boolean always_rename = COConfigurationManager.getBooleanParameter("Watch Torrent Always Rename");
			
			boolean use_tod = COConfigurationManager.getBooleanParameter( ConfigKeys.File.BCFG_WATCH_TORRENT_USE_TOD );
			
	    	int num_folders = COConfigurationManager.getIntParameter( "Watch Torrent Folder Path Count", 1);

	    	List<File>		folders = new ArrayList<>();
	    	List<String>	tags	= new ArrayList<>();

	    	for ( int i=0;i<num_folders;i++){
				String folder_path =
					COConfigurationManager.getStringParameter("Watch Torrent Folder Path" + (i==0?"":(" " + i )));

				File folder = null;

				if (folder_path != null && folder_path.length() > 0) {
					folder = FileUtil.newFile(folder_path);
					if (!folder.isDirectory()) {
						if (!folder.exists()) {
							FileUtil.mkdirs(folder);
						}
						if (!folder.isDirectory()) {
							if (Logger.isEnabled())
								Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
										"[Watch Torrent Folder Path] " + "does not exist or "
												+ "is not a dir"));
							folder = null;
						}
					}
				}

				if ( folder != null ){

					folders.add( folder );

					String tag =
							COConfigurationManager.getStringParameter("Watch Torrent Folder Tag" + (i==0?"":(" " + i )), null);

					if ( tag != null && tag.trim().length() == 0 ){

						tag = null;
					}

					tags.add( tag );
				}
	    	}

			if (folders.isEmpty()) {
				if (Logger.isEnabled())
					Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
							"[Watch Torrent Folder Path] not configured"));
				return;
			}

			String default_data_save_path = COConfigurationManager.getStringParameter("Default save path");

			File f = null;
			if (default_data_save_path != null && default_data_save_path.length() > 0) {
				f = FileUtil.newFile(default_data_save_path);

				// Path is not an existing directory.
				if (!f.isDirectory()) {
					if (!f.exists()) {FileUtil.mkdirs(f);}

					// If path is still not a directory, abort.
					if (!f.isDirectory()) {
						if (Logger.isEnabled()) {
							Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
									"[Default save path] does not exist or is not a dir"));
						}
						Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR,
								"[Default save path] does not exist or is not a dir"));
						return;
					}
				}
			}

			// If we get here, and this is true, then data_save_path isn't valid.
			if (f == null){
				if (Logger.isEnabled()) {
 					Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
						"[Default save path] needs to be set for auto-.torrent-import to work"));
				}
				Logger.log(new LogAlert(LogAlert.UNREPEATABLE, LogAlert.AT_ERROR,
						"[Default save path] needs to be set for auto-.torrent-import to work"));
			}


			//delete torrents from the previous import run

			for (int i = 0; i < to_delete.size(); i++) {

				TOTorrent torrent = (TOTorrent) to_delete.get(i);

				try{
					log( "Deleting processed torrent: " + TorrentUtils.getTorrentFileName(torrent));
					
				}catch( Throwable e ){
					
					Debug.out( e );
				}
				
				try {
					TorrentUtils.delete(torrent);

				} catch (Throwable e) {

					Debug.printStackTrace(e);
				}
			}

			to_delete.clear();

			for (int folder_index=0;folder_index<folders.size();folder_index++){

				File	folder = folders.get(folder_index);

				log( "Processing " + folder );
				
				final String tag_name = tags.get(folder_index);

					// if we are saving torrents to the same location as we import them from
					// then we can't assume that its safe to delete the torrent after import!

				boolean	save_torrents = save_torrents_default;

				if (torrent_save_path.length() == 0
						|| FileUtil.newFile( torrent_save_path ).getAbsolutePath().equals(folder.getAbsolutePath())
						|| !FileUtil.newFile(torrent_save_path).isDirectory()) {

					save_torrents = false;
				}

				boolean rename_to_imported = always_rename || !save_torrents;
						
				String[] currentFileList = folder.list(filename_filter);

				if (currentFileList == null) {
					Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
							"There was a problem trying to get a listing of torrents from " + folder));

				}else{

					for (int i = 0; i < currentFileList.length; i++) {

						if ( !running ){

							return;
						}

						File file = FileUtil.newFile(folder, currentFileList[i]).getAbsoluteFile();
						
						if ( file.getName().toLowerCase( Locale.US ).endsWith( ".magnet" )) {
							
							handleMagnet( file );
							
						}else{
							// make sure we've got a valid torrent file before proceeding
								
							log( "Importing " + file.getName());
							
							try {
	
								TOTorrent new_torrent = TorrentUtils.readFromFile(file, false);
	
								DownloadManager existing_dm = global_manager.getDownloadManager( new_torrent );
								
								if ( existing_dm != null) {
	
									if (Logger.isEnabled())
										Logger.log(new LogEvent(LOGID, file.getAbsolutePath()
												+ " is already being downloaded"));
										
										// check to see if we can rename the torrent file

									if ( rename_to_imported ){
									
										if ( !file.equals( FileUtil.newFile( existing_dm.getTorrentFileName()).getAbsoluteFile())){
											
											renameToImported( folder, file );
										}
									}
									
									applyTag( existing_dm, tag_name );

									TOTorrent existing_torrent	= existing_dm.getTorrent();
											
									if ( 	existing_torrent != null && 
											!existing_torrent.getPrivate() && 
											!new_torrent.getPrivate()){
									
										boolean can_merge = TorrentUtils.canMergeAnnounceURLs( new_torrent, existing_torrent);
									
										if ( can_merge ){
											
											TorrentUtils.mergeAnnounceURLs(	new_torrent, existing_torrent );
											
											TorrentUtils.writeToFile( existing_torrent );
											
											TRTrackerAnnouncer tc = existing_dm.getTrackerClient();
											
											if ( tc != null ){

												tc.resetTrackerUrl( false );
											}
											
											log( "Merged trackers from " + file.getName() + " into existing download" );
										}
									}
									
									log( "Import ignored, download already present: " + file.getName());
									
								}else if ( plugin_dm.lookupDownloadStub( new_torrent.getHash()) != null ){
	
									// archived download
	
									if (Logger.isEnabled())
										Logger.log(new LogEvent(LOGID, file.getAbsolutePath()
												+ " is an archived download"));
	
									if ( rename_to_imported ){
	
										renameToImported( folder, file );
	
									}else{
	
										to_delete.add( new_torrent );
									}
	
									log( "Import ignored, download already archived: " + file.getName());
									
								}else{

									boolean[] to_skip = TorrentUtils.getSkipFiles( new_torrent );

									final DownloadManagerInitialisationAdapter dmia = new DownloadManagerInitialisationAdapter() {
	
										@Override
										public int
										getActions()
										{
											return( ACT_ASSIGNS_TAGS );
										}
	
										@Override
										public void
										initialised(
											DownloadManager 		dm,
											boolean 				for_seeding )
										{
											DiskManagerFileInfoSet file_info_set = dm.getDiskManagerFileInfoSet();

											DiskManagerFileInfo[] fileInfos = file_info_set.getFiles();

											DownloadManagerState dms = dm.getDownloadState();
											
											boolean reorder_mode = COConfigurationManager.getBooleanParameter("Enable reorder storage mode");
											int reorder_mode_min_mb = COConfigurationManager.getIntParameter("Reorder storage mode min MB");
											
											if ( to_skip != null ){
												
												boolean[] toCompact = new boolean[fileInfos.length];
												boolean[] toReorderCompact = new boolean[fileInfos.length];

												int comp_num = 0;
												int reorder_comp_num = 0;
												
												try{
													dms.suppressStateSave(true);
	
													for (int i = 0; i < fileInfos.length; i++) {
													
														if ( to_skip[i] ){
															
															DiskManagerFileInfo fileInfo = fileInfos[i];

																// Always pull destination file from fileInfo and not from
																// TorrentFileInfo because the destination may have changed
																// by magic code elsewhere
														
															File fDest = fileInfo.getFile(true);
														
															if (!fDest.exists()) {

																if (reorder_mode
																		&& (fileInfo.getLength() / (1024 * 1024)) >= reorder_mode_min_mb) {

																	toReorderCompact[i] = true;

																	reorder_comp_num++;

																} else {

																	toCompact[i] = true;

																	comp_num++;
																}
															}
														}
													}
													
													file_info_set.setSkipped( to_skip, true );
													
													if (comp_num > 0) {

														file_info_set.setStorageTypes(toCompact, DiskManagerFileInfo.ST_COMPACT);
													}

													if (reorder_comp_num > 0) {

														file_info_set.setStorageTypes(toReorderCompact,	DiskManagerFileInfo.ST_REORDER_COMPACT);
													}
													
												}finally{
													
													dms.suppressStateSave( false );
												}
											}
											
											applyTag( dm, tag_name );
											
											applyAutoTagging( dm );
											
											TorrentOpenOptions.addModeDuringCreate( start_mode, dm );
										}
									};
	
									byte[] hash = null;
									
									try{
										hash = new_torrent.getHash();
										
									}catch( Exception e ){
									}
	
									String data_save_path = default_data_save_path;
									
									boolean for_seeding = false;
									
									if ( tag_name != null ){
										
											// if we have a tag with a move-on-complete destination and the files are already
											// there then fix things to it gets added-for-seeding
										
										TagManager tm = TagManagerFactory.getTagManager();

										TagType tt = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL );

										Tag	tag = tt.getTag( tag_name, true );
										
										if ( tag instanceof TagFeatureFileLocation ){
											
											TagFeatureFileLocation tag_save_location = (TagFeatureFileLocation)tag;
										
											if ( tag_save_location.supportsTagMoveOnComplete()){
												
												File move_loc = tag_save_location.getTagMoveOnCompleteFolder();
												
												if ( move_loc != null && move_loc.exists()){
												
													File root;
													
													if ( new_torrent.isSimpleTorrent()){
													
														root = move_loc;
														
													}else{
														
														root = FileUtil.newFile( move_loc, FileUtil.convertOSSpecificChars( TorrentUtils.getLocalisedName( new_torrent ), true ));
													}
													
													if (( tag_save_location.getTagMoveOnCompleteOptions() & TagFeatureFileLocation.FL_DATA ) != 0 ){
																																										
														TOTorrentFile[] files = new_torrent.getFiles();
														
														boolean all_exist = true;
														
														for ( int j=0;j<files.length;j++){
															
															if ( to_skip != null && to_skip[j] ){
																
																continue;
															}
															
															File file_loc = FileUtil.newFile( root, files[j].getRelativePath());
															
															if ( !file_loc.exists()){
																
																all_exist = false;
																
																break;
															}
														}
														
														if ( all_exist ){
															
															data_save_path = move_loc.getAbsolutePath();
															
															for_seeding = true;
														}
													}
												}
											}
										}
									}
																		
									if ( !use_tod ){
										
										int start_state = TorrentOpenOptions.addModePreCreate(start_mode);

										DownloadManager new_dm;
										
										if ( rename_to_imported ){
		
											File imported = renameToImported( folder, file );
		
											new_dm = global_manager.addDownloadManager(imported.getAbsolutePath(), hash,
													data_save_path, start_state, true, for_seeding, dmia);
											
		
										} else {
		
											new_dm = global_manager.addDownloadManager(file.getAbsolutePath(), hash,
													data_save_path, start_state, true, for_seeding, dmia);
		
											// add torrent for deletion, since there will be a
											// saved copy elsewhere
											
											to_delete.add( new_torrent );
										}
											
										log( "Imported " + file.getName());
										
											// might have already existed, check tagging
											
										applyTag( new_dm, tag_name );
										
										TorrentOpenOptions.addModePostCreate(start_mode, new_dm );	
										
										if (Logger.isEnabled()){
											Logger.log(new LogEvent(LOGID, "Auto-imported "
													+ file.getAbsolutePath()));
										}
									
									}else{
										
										TorrentOpenOptions torrentOptions = new TorrentOpenOptions( null );

										File to_file;
										
										if ( rename_to_imported ){
										
											to_file = renameToImported( folder, file );
											
										}else{

											TOTorrent copy = TorrentUtils.cloneTorrent( new_torrent );

												// delete immediately
												// to_delete.add( torrent );

											try{
												log( "Deleting processed torrent: " + TorrentUtils.getTorrentFileName( new_torrent ));
												
											}catch( Throwable e ){
												
												Debug.out( e );
											}
											
											try{
												TorrentUtils.delete( new_torrent );

											}catch( Throwable e ){

												Debug.printStackTrace(e);
											}
											
											new_torrent = copy;
											
											to_file = AETemporaryFileHandler.createTempFile();
											
											TorrentUtils.writeToFile( new_torrent, to_file, false );											
										}
										
										TOTorrent to_torrent = TorrentUtils.readFromFile( to_file, false );
												
										torrentOptions.setDeleteFileOnCancel( false );
										torrentOptions.setTorrentFile( to_file.getAbsolutePath());
										torrentOptions.setTorrent( to_torrent );
																					
										torrentOptions.setStartMode( start_mode );						
										
										if ( tag_name != null ){
											
											TagManager tm = TagManagerFactory.getTagManager();

											TagType tt = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL );

											Tag	tag = tt.getTag( tag_name, true );

											try{
												if ( tag == null ){

													tag = tt.createTag( tag_name, true );
												}
												
												torrentOptions.setInitialTags( Arrays.asList( tag ));
												
											}catch( Throwable e ){
												
												Debug.out( e );
											}
										}
										
										UIFunctions uif = UIFunctionsManager.getUIFunctions();

										uif.addTorrentWithOptions( false, torrentOptions );
										
										log( "Imported (via options dialog) " + file.getName());

										if (Logger.isEnabled()){
											Logger.log(new LogEvent(LOGID, "Auto-imported (via options dialog) "
													+ file.getAbsolutePath()));
										}

									}
								}
							} catch (Throwable e) {
	
								log( "Failed to import " + file.getName() + ": " + Debug.getNestedExceptionMessage(e));
								
								Debug.out("Failed to auto-import torrent file '"
										+ file.getAbsolutePath() + "' - "
										+ Debug.getNestedExceptionMessage(e));
								Debug.printStackTrace(e);
							}
						}
					}
				}
			}
		} finally {
			this_mon.exit();
		}
	}

	private void
	applyTag(
		DownloadManager		dm,
		String				tag_name )
	{
		if ( tag_name != null && dm != null ){
			
			TagManager tm = TagManagerFactory.getTagManager();

			TagType tt = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL );

			Tag	tag = tt.getTag( tag_name, true );

			try{
				if ( tag == null ){

					tag = tt.createTag( tag_name, true );
				}

				if ( !tag.hasTaggable( dm )){
				
					tag.addTaggable( dm );
				}
			}catch( Throwable e ){

				Debug.out( e );
			}
		}
	}
	
	private void
	applyAutoTagging(
		DownloadManager		dm )
	{
		if ( !COConfigurationManager.getBooleanParameter( "Files Auto Tag Enable" )){
			
			return;
		}
		
		Map<String,long[]>	ext_map = new HashMap<>();
		
		DiskManagerFileInfoSet file_info_set = dm.getDiskManagerFileInfoSet();

		DiskManagerFileInfo[] files = file_info_set.getFiles();

		for ( DiskManagerFileInfo file: files ){
			
			if ( file.isSkipped()){
				
				continue;
			}
			
			String ext = file.getExtension();			
				
			if ( ext != null && ext.startsWith(".")){
					
				ext = ext.substring( 1 );
				
				long file_size = file.getLength();
				
				long[] size = ext_map.get( ext );
				
				if ( size == null ){
					
					ext_map.put( ext, new long[]{ file_size });
					
				}else{
					
					size[0] += file_size;
				}
			}
		}
		
		int num = COConfigurationManager.getIntParameter( "Files Auto Tag Count" );
		
		TagManager tm = TagManagerFactory.getTagManager();	

		TagType tag_type = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL );
		
		List<Tag>	matched_tags	= new ArrayList<>();
		Tag			max_match_tag	= null;
		long		max_match_size	= -1;
		
		for ( int i=0; i<num; i++ ){
			
			String exts = COConfigurationManager.getStringParameter( "File Auto Tag Exts " + (i==0?"":(" " + i )), "");
			
			exts = exts.trim().toLowerCase( Locale.US );
			
			if ( exts.isEmpty()){
				
				continue;
			}
			
			String tag_name 	= COConfigurationManager.getStringParameter( "File Auto Tag Name " + (i==0?"":(" " + i )), "");
			
			tag_name = tag_name.trim();
			
			if ( tag_name.isEmpty()){
				
				continue;
			}
			
			try{			
				Tag tag = tag_type.getTag( tag_name,  true );
				
				if ( tag == null ){
					
					tag = tag_type.createTag( tag_name, true );
					
					tag.setPublic( false );
				}
				
				String[] bits = exts.replaceAll( ",", ";" ).split( ";" );
			
				boolean	matched		= false;
				long	max_match 	= 0;
			
				for ( String bit: bits ){
					
					bit = bit.trim();
					
					if ( bit.startsWith( "." )){
						
						bit = bit.substring( 1 );
					}
					
					long[] size = ext_map.get( bit );
					
					if ( size != null ){
						
						matched = true;
						
						if ( size[0] > max_match ){
							
							max_match = size[0];
						}
					}
				}
				
				if ( matched ){
					
					matched_tags.add( tag );
					
					if ( max_match > max_match_size ){
						
						max_match_size 	= max_match;
						max_match_tag	= tag;
					}
				}
			}catch( Throwable e ){
				
				Debug.out( e );
			}
		}
		
		List<Tag>	selected_tags = new ArrayList<>();
		
		if ( matched_tags.isEmpty()){
			
			String def_tag = COConfigurationManager.getStringParameter( "File Auto Tag Name Default", "" );

			def_tag = def_tag.trim();
			
			if ( !def_tag.isEmpty()){
			
				try{
					Tag tag = tag_type.getTag( def_tag,  true );
					
					if ( tag == null ){
						
						tag = tag_type.createTag( def_tag, true );
						
						tag.setPublic( false );
					}
					
					selected_tags.add( tag );
						
				}catch( Throwable e ){
					
					Debug.out( e );
				}
			}
		}else{
		
			boolean tag_best = COConfigurationManager.getBooleanParameter( "Files Auto Tag Best Size" );
			
			if ( tag_best ){
				
				selected_tags.add( max_match_tag );
				
			}else{
				
				selected_tags.addAll( matched_tags );
			}
		}
		
		for ( Tag t: selected_tags ){
		
			t.addTaggable( dm );
		}
	}
	
	private List<File>	pending_magnets = new ArrayList<File>();
	private Set<File>	active_magnets 	= new HashSet<File>();
	private Set<File>	failed_magnets 	= new HashSet<File>();
	
	private void
	handleMagnet(
		File		file )
	{
		// synced here
		
		if ( active_magnets.contains( file ) || failed_magnets.contains( file ) || pending_magnets.contains( file )){
			
			return;
		}
		
		log( "Adding magnet to queue: " + file.getName());
		
		pending_magnets.add( file );
		
		if ( active_magnets.size() >= 5 ){
			
			return;
		}
				
		File to_do = pending_magnets.remove( 0 );
		
		active_magnets.add( to_do );

		new AEThread2( "FolderWatcher:magnetdl")
		{
			public void
			run()
			{
				File	active = to_do;
				
				while( true ){
				
					boolean		ok 			= false;
					boolean		bad_magnet 	= true;
					
					try{
						log( "Processing magnet: " + active.getName());
						
						String magnet_uri = FileUtil.readFileAsString( active, 32000, "UTF-8" );
						
						URL magnet_url = new URL( magnet_uri );
						
						if ( !magnet_url.getProtocol().toLowerCase( Locale.US ).equals( "magnet" )){
							
							throw( new Exception( "URL '" + magnet_url + "' is not magnet protocol" ));
						}
						
						bad_magnet = false;
						
						File output_file = FileUtil.newFile( active.getAbsolutePath() + ".torrent" );
						
						if ( output_file.exists()){
							
							output_file.delete();
						}
						
						MagnetConnection2 con = (MagnetConnection2)magnet_url.openConnection();
						
						try{
							con.connect();
							
							FileUtil.copyFile( con.getInputStream(), output_file );
							
							if ( output_file.length() == 0 ){
								
								output_file.delete();
								
								throw( new Exception( "Magnet download failed" ));
							}
							
							ok = true;
						
						}finally {
							
							con.disconnect();
						}
					}catch( Throwable e ){
						
						Debug.out( "Failed to auto-import magnet file '" + active.getAbsolutePath() + "' - " + Debug.getNestedExceptionMessage( e ));
						
						Debug.printStackTrace(e);
						
					}finally{
						
						try{
							this_mon.enter();
						
							active_magnets.remove( active );
							
							if ( ok ){
							
								log( "Completed magnet: " + active.getName());
								
								active.delete();
								
							}else{
								
								if ( bad_magnet ){
								
									log( "Bad magnet, failing: " + active.getName());
									
									active.renameTo( FileUtil.newFile( active.getAbsolutePath() + ".failed" ));
									
								}else{
									
									log( "Magnet download failed, will retry later: " + active.getName());
								}
								
								failed_magnets.add( active );
							}
							
							if ( pending_magnets.isEmpty()){
								
								if ( active_magnets.isEmpty()){
									
									failed_magnets.clear();
								}
								
								break;
								
							}else{
								
								active = pending_magnets.remove( 0 );
								
								active_magnets.add( active );
							}
						}finally{
						
							this_mon.exit();
						}
					}
				}
			}
		}.start();
	}
	
	private void
	log(
		String		str )
	{
		log.log( str );
	}
}
