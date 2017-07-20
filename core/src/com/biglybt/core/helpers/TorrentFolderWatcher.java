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
import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.Core;
import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerInitialisationAdapter;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.logging.LogAlert;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;

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

	volatile boolean running = false;

	private final ArrayList<TOTorrent> to_delete = new ArrayList<>();

	protected final AEMonitor this_mon = new AEMonitor("TorrentFolderWatcher");

	private final FilenameFilter filename_filter = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String name) {
			String lc_name = name.toLowerCase();

			return (lc_name.endsWith(".torrent") || lc_name.endsWith(".tor"));
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

						if ( remaining < 250 ){

							remaining = 250;
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

	void importAddedFiles() {

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

			String torrent_save_path = COConfigurationManager
					.getStringParameter("General_sDefaultTorrent_Directory");

			int start_state = COConfigurationManager
					.getBooleanParameter("Start Watched Torrents Stopped")
					? DownloadManager.STATE_STOPPED : DownloadManager.STATE_QUEUED;

	    	int num_folders = COConfigurationManager.getIntParameter( "Watch Torrent Folder Path Count", 1);

	    	List<File>		folders = new ArrayList<>();
	    	List<String>	tags	= new ArrayList<>();

	    	for ( int i=0;i<num_folders;i++){
				String folder_path =
					COConfigurationManager.getStringParameter("Watch Torrent Folder Path" + (i==0?"":(" " + i )));

				File folder = null;

				if (folder_path != null && folder_path.length() > 0) {
					folder = new File(folder_path);
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

			String data_save_path = COConfigurationManager.getStringParameter("Default save path");

			File f = null;
			if (data_save_path != null && data_save_path.length() > 0) {
				f = new File(data_save_path);

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

				try {
					TorrentUtils.delete(torrent);

				} catch (Throwable e) {

					Debug.printStackTrace(e);
				}
			}

			to_delete.clear();

			for (int folder_index=0;folder_index<folders.size();folder_index++){

				File	folder = folders.get(folder_index);

				final String tag_name = tags.get(folder_index);

					// if we are saving torrents to the same location as we import them from
					// then we can't assume that its safe to delete the torrent after import!

				boolean	save_torrents = save_torrents_default;

				if (torrent_save_path.length() == 0
						|| new File( torrent_save_path ).getAbsolutePath().equals(folder.getAbsolutePath())
						|| !new File(torrent_save_path).isDirectory()) {

					save_torrents = false;
				}

				String[] currentFileList = folder.list(filename_filter);

				if (currentFileList == null) {
					Logger.log(new LogEvent(LOGID, LogEvent.LT_ERROR,
							"There was a problem trying to get a listing of torrents from " + folder));

				}else{

					for (int i = 0; i < currentFileList.length; i++) {

						if ( !running ){

							return;
						}

						File file = new File(folder, currentFileList[i]);

						// make sure we've got a valid torrent file before proceeding

						try {

							TOTorrent torrent = TorrentUtils.readFromFile(file, false);

							if (global_manager.getDownloadManager(torrent) != null) {

								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID, file.getAbsolutePath()
											+ " is already being downloaded"));

								// we can't touch the torrent file as it is (probably)
								// being used for the download
							}else if ( plugin_dm.lookupDownloadStub( torrent.getHash()) != null ){

								// archived download

								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID, file.getAbsolutePath()
											+ " is an archived download"));

								if ( !save_torrents ){

									File imported = new File(folder, file.getName() + ".imported");

									TorrentUtils.move(file, imported);

								}else{

									to_delete.add(torrent);
								}

							} else {

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
										if ( tag_name != null ){

											TagManager tm = TagManagerFactory.getTagManager();

											TagType tt = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL );

											Tag	tag = tt.getTag( tag_name, true );

											try{
												if ( tag == null ){

													tag = tt.createTag( tag_name, true );
												}

												tag.addTaggable( dm );

											}catch( Throwable e ){

												Debug.out( e );
											}
										}
									}
								};

								byte[] hash = null;
								try {
									hash = torrent.getHash();
								} catch (Exception e) { }

								if (!save_torrents) {

									File imported = new File(folder, file.getName() + ".imported");

									TorrentUtils.move(file, imported);

									global_manager.addDownloadManager(imported.getAbsolutePath(), hash,
											data_save_path, start_state, true,false,dmia);

								} else {

									global_manager.addDownloadManager(file.getAbsolutePath(), hash,
											data_save_path, start_state, true, false, dmia);

									// add torrent for deletion, since there will be a
									// saved copy elsewhere
									to_delete.add(torrent);
								}

								if (Logger.isEnabled())
									Logger.log(new LogEvent(LOGID, "Auto-imported "
											+ file.getAbsolutePath()));
							}

						} catch (Throwable e) {

							Debug.out("Failed to auto-import torrent file '"
									+ file.getAbsolutePath() + "' - "
									+ Debug.getNestedExceptionMessage(e));
							Debug.printStackTrace(e);
						}
					}
				}
			}
		} finally {
			this_mon.exit();
		}
	}

}