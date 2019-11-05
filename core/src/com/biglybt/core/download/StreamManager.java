/*
 * Created on Jun 21, 2010
 * Created by Paul Gardner
 *
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
 */


package com.biglybt.core.download;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.devices.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.PluginListener;
import com.biglybt.pif.PluginManager;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadStats;
import com.biglybt.pif.torrent.TorrentAttribute;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;

public class
StreamManager
{
	private static final int BUFFER_SECS_DEFAULT 		= 30;
	private static final int BUFFER_MIN_SECS_DEFAULT	= 3;

	static int config_buffer_secs;
	static int config_min_buffer_secs;

	static{
		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"streamman.buffer.secs",
				"streamman.min.buffer.secs",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String parameterName)
				{
					config_buffer_secs 		= COConfigurationManager.getIntParameter( "streamman.buffer.secs", BUFFER_SECS_DEFAULT );
					config_min_buffer_secs 	= COConfigurationManager.getIntParameter( "streamman.min.buffer.secs", BUFFER_MIN_SECS_DEFAULT );
				}
			});

	}

	private static StreamManager		singleton = new StreamManager();

	public static StreamManager
	getSingleton()
	{
		return( singleton );
	}

	TorrentAttribute	mi_ta;

	AsyncDispatcher	dispatcher = new AsyncDispatcher();

	List<SMDImpl>		streamers = new ArrayList<>();

	private
	StreamManager()
	{
		PluginInterface default_pi = PluginInitializer.getDefaultInterface();

		mi_ta = default_pi.getTorrentManager().getPluginAttribute( "sm_metainfo" );

		default_pi.addListener(
			new PluginListener()
			{
				@Override
				public void
				initializationComplete()
				{

				}

				@Override
				public void
				closedownInitiated()
				{
					dispatcher.dispatch(
						new AERunnable()
						{
							@Override
							public void
							runSupport()
							{
								List<SMDImpl>	to_cancel;

								synchronized( StreamManager.this ){

									to_cancel = new ArrayList<>(streamers);

									streamers.clear();
								}

								for ( SMDImpl s: to_cancel ){

									s.cancel();
								}
							}
						});
				}

				@Override
				public void
				closedownComplete()
				{

				}
			});
	}

	public int
	getBufferSecs()
	{
		return( config_buffer_secs );
	}

	public void
	setBufferSecs(
		int		secs )
	{
		COConfigurationManager.setParameter( "streamman.buffer.secs", secs );
	}

	public int
	getMinBufferSecs()
	{
		return( config_min_buffer_secs );
	}

	public void
	setMinBufferSecs(
		int		secs )
	{
		COConfigurationManager.setParameter( "streamman.min.buffer.secs", secs );
	}

	public boolean
	isStreamingUsable()
	{
			// need win or osx 10.5+
		// linux supported since 2101
		/*
		if ( !( Constants.isWindows || Constants.isOSX )){

			return( false );
		}
		*/
		
		try{
			PluginManager plug_man = CoreFactory.getSingleton().getPluginManager();

			PluginInterface xcode_pi = plug_man.getPluginInterfaceByID( "vuzexcode", false );

			if ( xcode_pi != null && !xcode_pi.getPluginState().isOperational()){

					// can't use if xcode borked

				return( false );
			}

				// otherwise xcode will be installed on demand

			PluginInterface emp_pi = plug_man.getPluginInterfaceByID( "azemp", false );

			if ( emp_pi == null ){

					// will be installed on demand

				return( true );
			}

			if ( !emp_pi.getPluginState().isOperational()){

					// can't use if emp borked

				return( false );
			}

				// emp installed but need version with prepareWindow, wait for update

			Class<?> epwClass = emp_pi.getPlugin().getClass().getClassLoader().loadClass( "com.azureus.plugins.azemp.ui.swt.emp.EmbeddedPlayerWindowSWT" );

			Method method = epwClass.getMethod( "prepareWindow", new Class[] { String.class });

			return( method != null );

		}catch( Throwable e ){

			return( false );
		}
	}

	public StreamManagerDownload
	stream(
		DownloadManager					dm,
		int								file_index,
		URL								url,
		boolean							preview_mode,
		StreamManagerDownloadListener	listener )
	{
		SMDImpl	result = new SMDImpl( dm, file_index, url, preview_mode, listener );

		synchronized( StreamManager.this ){

			streamers.add( result );
		}

		return( result );
	}


	private class
	SMDImpl
		extends AERunnable
		implements StreamManagerDownload
	{
		DownloadManager						dm;
		int									file_index;
		URL									url;
		StreamManagerDownloadListener		listener;

		private int						existing_dl_limit;

		boolean					preview_mode;
		private long					preview_mode_last_change = 0;

		private AESemaphore				active_sem;
		private TranscodeJob			active_job;

		EnhancedDownloadManager	active_edm;
		private boolean					active_edm_activated;

		volatile boolean		cancelled;

		SMDImpl(
			DownloadManager					_dm,
			int								_file_index,
			URL								_url,
			boolean							_preview_mode,
			StreamManagerDownloadListener	_listener )
		{
			dm				= _dm;
			file_index		= _file_index;
			url				= _url;
			preview_mode	= _preview_mode;
			listener		= _listener;

			dispatcher.dispatch( this );
		}

		@Override
		public DownloadManager
		getDownload()
		{
			return( dm );
		}

		@Override
		public int
		getFileIndex()
		{
			return( file_index );
		}

		@Override
		public URL
		getURL()
		{
			return( url );
		}

		@Override
		public boolean
		getPreviewMode()
		{
			return( preview_mode );
		}

		@Override
		public void
		setPreviewMode(
			boolean	_preview_mode )
		{
			long	now = SystemTime.getMonotonousTime();

			if ( 	preview_mode_last_change == 0 ||
					now - preview_mode_last_change > 500 ){

				preview_mode_last_change = now;

				preview_mode = _preview_mode;

				listener.updateActivity( "Preview mode changed to " + preview_mode );
			}
		}

		@Override
		public void
		runSupport()
		{
			try{
				synchronized( StreamManager.this ){

					if ( cancelled ){

						throw( new Exception( "Cancelled" ));
					}

					active_edm = DownloadManagerEnhancer.getSingleton().getEnhancedDownload( dm );
				}

				final long stream_start = SystemTime.getMonotonousTime();

				final Download download = PluginCoreUtils.wrap( dm );

				final DiskManagerFileInfo file = download.getDiskManagerFileInfo( file_index );

				PluginInterface emp_pi = checkPlugin( "azemp", "media player" );

				checkPlugin( "vuzexcode", "media analyser" );

				Class<?> epwClass = emp_pi.getPlugin().getClass().getClassLoader().loadClass( "com.azureus.plugins.azemp.ui.swt.emp.EmbeddedPlayerWindowSWT" );

				Method method = epwClass.getMethod( "prepareWindow", new Class[] { String.class });

				final Object player = method.invoke(null, new Object[] { file.getFile( true ).getName() });

				final Method buffering_method	= player.getClass().getMethod( "bufferingPlayback", new Class[] { Map.class });
				final Method is_active_method	= player.getClass().getMethod( "isActive", new Class[] {});

				final StreamManagerDownloadListener original_listener = listener;

				listener =
					new StreamManagerDownloadListener()
					{
						@Override
						public void
						updateActivity(
							String		str )
						{
							original_listener.updateActivity(str);
						}

						@Override
						public void
						updateStats(
							int			secs_until_playable,
							int			buffer_secs,
							long		buffer_bytes,
							int			target_buffer_secs )
						{
							original_listener.updateStats(secs_until_playable, buffer_secs, buffer_bytes, target_buffer_secs);
						}

						@Override
						public void
						ready()
						{
							original_listener.ready();
						}

						@Override
						public void
						failed(
							Throwable 	error )
						{
							try{
								original_listener.failed(error);

								Map<String,Object> b_map = new HashMap<>();

								b_map.put( "state", new Integer( 3 ));
								b_map.put( "msg", Debug.getNestedExceptionMessage( error ));

								try{
									buffering_method.invoke(player, new Object[] { b_map });

								}catch( Throwable e ){

									Debug.out( e );
								}
							}finally{

								cancel();
							}
						}
					};

				Map<String,Map<String,Object>>	map = (Map<String,Map<String,Object>>)download.getMapAttribute( mi_ta );

				Long	l_duration 		= null;
				Long	l_video_width 	= null;
				Long	l_video_height 	= null;

				if ( map != null ){

					Map<String,Object> file_map = map.get( String.valueOf( file_index ));

					if ( file_map != null ){

						l_duration 		= (Long)file_map.get( "duration" );
						l_video_width 	= (Long)file_map.get( "video_width" );
						l_video_height 	= (Long)file_map.get( "video_height" );
					}
				}

				final long duration;
				long video_width;
				long video_height;

				if ( l_duration == null ){

					active_edm.prepareForProgressiveMode( true );

					try{
						DeviceManager dm = DeviceManagerFactory.getSingleton();

						if ( dm == null ) {
							
							throw( new Exception( "sidebar (not tabbed) view required" ));
						}
						
						TranscodeManager tm = dm.getTranscodeManager();

						DeviceMediaRenderer dmr =
							(DeviceMediaRenderer)dm.addVirtualDevice(
								Device.DT_MEDIA_RENDERER,
								"18a0b53a-a466-6795-1d0f-cf38c830ca0e",
								"generic",
								"Media Analyser" );

						dmr.setHidden(true);
						dmr.setCanRemove(false);

						TranscodeQueue queue = tm.getQueue();

						TranscodeJob[] jobs = queue.getJobs();

						for ( TranscodeJob job: jobs ){

							if ( job.getTarget() == dmr ){

								job.removeForce();
							}
						}

						TranscodeProfile[] profiles = dmr.getTranscodeProfiles();

						TranscodeProfile profile = null;

						for (TranscodeProfile p : profiles) {

							if ( p.getName().equals( "Generic MP4" )){

								profile = p;

								break;
							}
						}

						if ( profile == null ){

							throw( new Exception( "Analyser transcode profile not found" ));
						}

						listener.updateActivity( "Analysing media" );

						final Map<String,Object> b_map = new HashMap<>();

						b_map.put( "state", new Integer( 1 ));
						b_map.put( "msg", MessageText.getString( "stream.analysing.media" ));

						buffering_method.invoke(player, new Object[] { b_map });

						final TranscodeJob tj = queue.add( dmr, profile, file, true );

						try{
							final AESemaphore sem = new AESemaphore( "analyserWait" );

							synchronized( StreamManager.this ){

								if ( cancelled ){

									throw( new Exception( "Cancelled" ));
								}

								active_sem	= sem;
								active_job 	= tj;
							}

							final long[] properties = new long[3];

							final Throwable[] error = { null };

							tj.analyseNow(
								new TranscodeAnalysisListener()
								{
									@Override
									public void
									analysisComplete(
										TranscodeJob					file,
										TranscodeProviderAnalysis		analysis )
									{
										try{
											properties[0] = analysis.getLongProperty( TranscodeProviderAnalysis.PT_DURATION_MILLIS );
											properties[1] = analysis.getLongProperty( TranscodeProviderAnalysis.PT_VIDEO_WIDTH );
											properties[2] = analysis.getLongProperty( TranscodeProviderAnalysis.PT_VIDEO_HEIGHT );

											tj.removeForce();

										}finally{

											sem.releaseForever();
										}
									}

									@Override
									public void
									analysisFailed(
										TranscodeJob		file,
										TranscodeException	e )
									{
										try{
											error[0] = e;

											tj.removeForce();

										}finally{

											sem.releaseForever();
										}
									}
								});

							new AEThread2( "SM:anmon" )
								{
									@Override
									public void
									run()
									{
										boolean	last_preview_mode = preview_mode;

										while( !sem.isReleasedForever() && !cancelled ){

											if ( !sem.reserve( 250 )){

												if ( cancelled ){

													return;
												}

												try{
													Boolean b = (Boolean)is_active_method.invoke( player, new Object[0] );

													if ( !b ){

														cancel();

														break;
													}
												}catch( Throwable e ){
												}

												if ( last_preview_mode != preview_mode ){

													last_preview_mode = preview_mode;

													b_map.put( "msg", MessageText.getString( last_preview_mode?"stream.analysing.media.preview":"stream.analysing.media" ));

												}
												DownloadStats stats = download.getStats();

												b_map.put( "dl_rate", stats.getDownloadAverage());
												b_map.put( "dl_size", stats.getDownloaded());
												b_map.put( "dl_time", SystemTime.getMonotonousTime() - stream_start );

												try{
													buffering_method.invoke(player, new Object[] { b_map });

												}catch( Throwable e ){

												}
											}
										}
									}
								}.start();

							sem.reserve();

							synchronized( StreamManager.this ){

								if ( cancelled ){

									throw( new Exception( "Cancelled" ));
								}

								active_job 	= null;
								active_sem	= null;
							}

							if ( error[0] != null ){

								throw( error[0] );
							}

							duration 		= properties[0];
							video_width		= properties[1];
							video_height	= properties[2];

							if ( duration > 0 ){

								if ( map == null ){

									map = new HashMap<>();

								}else{

									map = new HashMap<>(map);
								}

								Map<String,Object> file_map = map.get( String.valueOf( file_index ));

								if ( file_map == null ){

									file_map = new HashMap<>();

									map.put( String.valueOf( file_index ), file_map );
								}

								file_map.put( "duration", duration );
								file_map.put( "video_width", video_width );
								file_map.put( "video_height", video_height );

								download.setMapAttribute( mi_ta, map );
							}

						}catch( Throwable e ){

							tj.removeForce();

							throw( e );
						}

					}catch( Throwable e ){

						throw( new Exception( "Media analysis failed", e ));

					}finally{

					}
				}else{

					duration 		= l_duration;
					video_width		= l_video_width==null?0:l_video_width;
					video_height	= l_video_height==null?0:l_video_height;
				}

				if ( video_width == 0 || video_height == 0){

					throw( new Exception( "Media analysis failed - video stream not found" ));
				}

				if ( duration == 0 ){

					throw( new Exception( "Media analysis failed - duration unknown" ));
				}

				listener.updateActivity( "MetaData read: duration=" + TimeFormatter.formatColon( duration/1000) + ", width=" + video_width + ", height=" + video_height );

				Method smd_method = player.getClass().getMethod( "setMetaData", new Class[] { Map.class });

				Map<String,Object>	md_map = new HashMap<>();

				md_map.put( "duration", duration );
				md_map.put( "width", video_width );
				md_map.put( "height", video_height );

				smd_method.invoke( player, new Object[] { md_map });

				final long	bytes_per_sec = file.getLength() / (duration/1000);

				long	dl_lim_max 		= COConfigurationManager.getIntParameter( "Plugin.azemp.azemp.config.dl_lim_max" ) * 1024L;
				long	dl_lim_extra 	= COConfigurationManager.getIntParameter( "Plugin.azemp.azemp.config.dl_lim_extra" ) * 1024L;

				existing_dl_limit = download.getDownloadRateLimitBytesPerSecond();

				long	required_limit = Math.max( dl_lim_max, bytes_per_sec + dl_lim_extra );

				if ( required_limit > 0 ){

					download.setDownloadRateLimitBytesPerSecond((int)required_limit );
				}

				listener.updateActivity( "Average rate=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( bytes_per_sec ) + ", applied dl limit=" + DisplayFormatters.formatByteCountToKiBEtcPerSec( required_limit ));

				synchronized( StreamManager.this ){

					if ( cancelled ){

						throw( new Exception( "Cancelled" ));
					}

					active_edm.setExplicitProgressive( config_buffer_secs, bytes_per_sec, file_index );

					if ( !active_edm.setProgressiveMode( true )){

						throw( new Exception( "Failed to set download as progressive" ));
					}

					active_edm_activated = true;
				}

				new AEThread2( "streamMon" )
				{

					@Override
					public void
					run()
					{
						final int TIMER_PERIOD 		= 250;
						final int PLAY_STATS_PERIOD	= 5000;
						final int PLAY_STATS_TICKS	= PLAY_STATS_PERIOD / TIMER_PERIOD;

						final int DL_STARTUP_PERIOD	= 5000;
						final int DL_STARTUP_TICKS	= DL_STARTUP_PERIOD / TIMER_PERIOD;

						boolean playback_started 	= false;
						boolean	playback_paused		= false;

						boolean	error_reported = false;

						try{
							Method start_method 		= player.getClass().getMethod( "startPlayback", new Class[] { URL.class });
							Method pause_method 		= player.getClass().getMethod( "pausePlayback", new Class[] {});
							Method resume_method 		= player.getClass().getMethod( "resumePlayback", new Class[] {});
							Method buffering_method		= player.getClass().getMethod( "bufferingPlayback", new Class[] { Map.class });
							Method play_stats_method	= player.getClass().getMethod( "playStats", new Class[] { Map.class });

							int tick_count = 0;

							while( !cancelled ){

								tick_count++;

								int dm_state = dm.getState();

								boolean complete = file.getLength() == file.getDownloaded();

								if ( !complete ){

									if ( 	dm_state == DownloadManager.STATE_ERROR ||
											dm_state == DownloadManager.STATE_STOPPED ||
											dm_state == DownloadManager.STATE_QUEUED ){

										if ( tick_count >= DL_STARTUP_TICKS ){

											throw( new Exception( "Streaming abandoned, download isn't running" ));
										}
									}

									if ( !active_edm.getProgressiveMode()){

										complete = file.getLength() == file.getDownloaded();

										if ( !complete ){

											throw( new Exception( "Streaming mode abandoned for download" ));
										}
									}
								}

								long[] details = updateETA( active_edm );

								int		eta 		= (int)details[0];
								int		buffer_secs	= (int)details[1];
								long	buffer		= details[2];

								listener.updateStats( eta, buffer_secs, buffer, config_buffer_secs );

								boolean playable;

								int	buffer_to_use = playback_started?config_min_buffer_secs:config_buffer_secs;

								if ( complete ){

									playable = true;

								}else{

									playable = buffer_secs > buffer_to_use;

									playable = playable && ( eta <= 0  || (playback_started && !playback_paused ) || preview_mode );
								}

								if ( playback_started ){

									if ( playable ){

										if ( playback_paused ){

											listener.updateActivity( "Resuming playback" );

											resume_method.invoke(player, new Object[] {});

											playback_paused = false;
										}
									}else{

										if ( !playback_paused ){

											listener.updateActivity( "Pausing playback to prevent stall" );

											pause_method.invoke(player, new Object[] {});

											playback_paused = true;
										}
									}
								}else{

									if ( playable ){

										listener.ready();

										start_method.invoke(player, new Object[] { url });

										playback_started = true;
									}
								}

								if ( playable ){

									if ( tick_count % PLAY_STATS_TICKS == 0 ){

										long contiguous_done = active_edm.getContiguousAvailableBytes( file_index>=0?file_index:active_edm.getPrimaryFileIndex(), 0, 0 );

										Map<String,Object> map = new HashMap<>();

										map.put( "buffer_min", new Long( config_buffer_secs ));
										map.put( "buffer_secs", new Integer( buffer_secs ));
										map.put( "buffer_bytes", new Long( buffer ));

										map.put( "stream_rate", bytes_per_sec );

										DownloadStats stats = download.getStats();

										map.put( "dl_rate", stats.getDownloadAverage());
										map.put( "dl_size", stats.getDownloaded());
										map.put( "dl_time", SystemTime.getMonotonousTime() - stream_start );

										map.put( "duration", duration );
										map.put( "file_size", file.getLength());
										map.put( "cont_done", contiguous_done );

										play_stats_method.invoke(player, new Object[] { map });
									}
								}else{

									DownloadStats stats = download.getStats();

									Map<String,Object> map = new HashMap<>();

									map.put( "state", new Integer( 2 ));

									if ( preview_mode && !complete ){

										long rate = stats.getDownloadAverage();

										int	preview_eta;

										if ( rate <= 0 ){

											preview_eta = Integer.MAX_VALUE;

										}else{

											double secs_per_sec = ((double)bytes_per_sec)/rate;

											preview_eta = (int)(( buffer_to_use - buffer_secs ) * secs_per_sec);
										}

										map.put( "eta", new Integer( preview_eta ));

										map.put( "preview", 1 );

									}else{

										map.put( "eta", new Integer( eta ));

										map.put( "preview", 0 );
									}

									map.put( "buffer_min", new Long( config_buffer_secs ));
									map.put( "buffer_secs", new Integer( buffer_secs ));
									map.put( "buffer_bytes", new Long( buffer ));

									map.put( "stream_rate", bytes_per_sec );

									map.put( "dl_rate", stats.getDownloadAverage());
									map.put( "dl_size", stats.getDownloaded());
									map.put( "dl_time", SystemTime.getMonotonousTime() - stream_start );

									buffering_method.invoke(player, new Object[] { map });
								}

								Thread.sleep( TIMER_PERIOD );

								try{
									Boolean b = (Boolean)is_active_method.invoke( player, new Object[0] );

									if ( !b ){

										cancel();

										break;
									}
								}catch( Throwable e ){
								}
							}
						}catch( Throwable e ){

							error_reported = true;

							listener.failed( e );

						}finally{

							if ( !( error_reported || cancelled )){

								if ( !playback_started ){

									listener.failed( new Exception( "Streaming failed, reason unknown" ));
								}
							}
						}
					}
				}.start();

			}catch( Throwable e ){

				try{
					listener.failed( e );

				}finally{

					cancel();
				}
			}
		}

		long[]
		updateETA(
			EnhancedDownloadManager 	edm )
		{
			long _eta = edm.getProgressivePlayETA();

			int	eta = _eta>=Integer.MAX_VALUE?Integer.MAX_VALUE:(int)_eta;

			EnhancedDownloadManager.progressiveStats stats = edm.getProgressiveStats();

			long provider_pos = stats.getCurrentProviderPosition( false );

			long buffer = edm.getContiguousAvailableBytes( file_index>=0?file_index:edm.getPrimaryFileIndex(), provider_pos, 0 );

			long bps = stats.getStreamBytesPerSecondMin();

			int	buffer_secs = bps<=0?Integer.MAX_VALUE:(int)(buffer/bps);

			return( new long[]{ eta, buffer_secs, buffer });
		}

		@Override
		public void
		cancel()
		{
			TranscodeJob	job;

			EnhancedDownloadManager	edm;
			boolean					edm_activated;

			synchronized( StreamManager.this ){

				cancelled = true;

				job = active_job;

				if ( active_sem != null ){

					active_sem.releaseForever();
				}

				edm 			= active_edm;
				edm_activated	= active_edm_activated;

				streamers.remove( this );
			}

			if ( job != null ){

				job.removeForce();
			}

			if ( edm != null ){

				if ( edm_activated ){

					edm.setProgressiveMode( false );

				}else{

					edm.prepareForProgressiveMode( false );
				}
			}

			final Download download = PluginCoreUtils.wrap( dm );

			download.setDownloadRateLimitBytesPerSecond( existing_dl_limit );
		}

		@Override
		public boolean
		isCancelled()
		{
			return( cancelled );
		}

		private PluginInterface
		checkPlugin(
			String		id,
			String		name )

			throws Throwable
		{
			PluginManager plug_man = CoreFactory.getSingleton().getPluginManager();

			PluginInterface pi = plug_man.getPluginInterfaceByID( id, false );

			if ( pi == null ){

				UIFunctions uif = UIFunctionsManager.getUIFunctions();

				if ( uif == null ){

					throw( new Exception( "UIFunctions unavailable - can't install plugin '" + name + "'" ));
				}

				listener.updateActivity( "Installing " + name );

				final AESemaphore sem = new AESemaphore( "analyserWait" );

				synchronized( StreamManager.this ){

					if ( cancelled ){

						throw( new Exception( "Cancelled" ));
					}

					active_sem	= sem;
				}

				final Throwable[] error = { null };

				uif.installPlugin(
						id,
	    				"dlg.install." + id,
	    				new UIFunctions.actionListener()
						{
							@Override
							public void
							actionComplete(
								Object		result )
							{
								try{
									if ( result instanceof Boolean ){

									}else{

										error[0] = (Throwable)result;
									}
								}finally{

									sem.release();
								}
							}
						});

				sem.reserve();

				synchronized( StreamManager.this ){

					if ( cancelled ){

						throw( new Exception( "Cancelled" ));
					}

					active_sem	= null;
				}

				if( error[0] != null ){

					throw( error[0] );
				}

				long start = SystemTime.getMonotonousTime();

				listener.updateActivity( "Waiting for plugin initialisation" );

				while( true ){

					if ( cancelled ){

						throw( new Exception( "Cancelled" ));
					}

					if ( SystemTime.getMonotonousTime() - start >= 30*1000 ){

						throw( new Exception( "Timeout waiting for " + name + " to initialise" ));
					}

					pi = plug_man.getPluginInterfaceByID( id, false );

					if ( pi != null && pi.getPluginState().isOperational()){

						return( pi );
					}

					Thread.sleep(250);
				}
			}else if ( !pi.getPluginState().isOperational()){

				throw( new Exception( name + " not operational" ));

			}else{

				return( pi );
			}
		}
	}
}
