/*
 * Created on Feb 6, 2009
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


package com.biglybt.core.devices.impl;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.devices.*;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.download.DiskManagerFileInfoURL;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.*;
import com.biglybt.core.util.average.Average;
import com.biglybt.core.util.average.AverageFactory;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.disk.DiskManager;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ipc.IPCInterface;
import com.biglybt.pif.utils.DelayedTask;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.utils.UtilitiesImpl;

public class
TranscodeQueueImpl
	implements TranscodeQueue
{
	private static final String	CONFIG_FILE 			= "xcodejobs.config";

	private static final Object KEY_XCODE_ERROR	= new Object();

	private TranscodeManagerImpl		manager;

	List<TranscodeJobImpl>		queue		= new ArrayList<>();
	AESemaphore 				queue_sem 	= new AESemaphore( "XcodeQ" );
	AEThread2					queue_thread;

	private volatile TranscodeJobImpl	current_job;

	private AsyncDispatcher	anaylsis_dispatcher = new AsyncDispatcher();

	private CopyOnWriteList<TranscodeQueueListener>			listeners = new CopyOnWriteList<>();
	private CopyOnWriteList<TranscodeQueueActionListener>	action_listeners = new CopyOnWriteList<>();

	volatile boolean 	paused;
	volatile int		max_bytes_per_sec;

	volatile boolean	config_dirty;

	protected
	TranscodeQueueImpl(
		TranscodeManagerImpl	_manager )
	{
		manager = _manager;
	}

	protected void
	initialise()
	{
		loadConfig();

		COConfigurationManager.addAndFireParameterListeners(
			new String[]{
				"xcode.queue.paused",
				"xcode.queue.maxbps",
			},
			new ParameterListener()
			{
				@Override
				public void
				parameterChanged(
					String		name )
				{
					paused				= COConfigurationManager.getBooleanParameter( "xcode.queue.paused", false );
					max_bytes_per_sec	= COConfigurationManager.getIntParameter( "xcode.queue.maxbps", 0 );
				}
			});

		DelayedTask delayed_task =
			UtilitiesImpl.addDelayedTask(
				"TranscodeQueue:schedule",
				new Runnable()
				{
					@Override
					public void
					run()
					{
						schedule();
					}
				});

		delayed_task.queue();
	}

	protected boolean
	process(
		final TranscodeJobImpl		job )
	{
		TranscodePipe pipe = null;

		current_job = job;

		DeviceImpl	device = job.getDevice();

		device.setTranscoding( true );

		try{
			job.starts();

			TranscodeProvider provider = job.getProfile().getProvider();

			final TranscodeException[] error = { null };

			TranscodeProfile profile = job.getProfile();

			final TranscodeFileImpl		transcode_file = job.getTranscodeFile();

			TranscodeProviderAnalysis 	provider_analysis;

			boolean xcode_required;

			if ( provider == null ){

				xcode_required = false;

				provider_analysis = null;

			}else{

				provider_analysis = analyse( job );

				xcode_required 	= provider_analysis.getBooleanProperty( TranscodeProviderAnalysis.PT_TRANSCODE_REQUIRED );

				int	tt_req;

				if ( job.isStream()){

						// already advertised as a transcoded asset so no option not to
						// transcode (as name/format would change if decided not to transcode and then
						// this would confuse the clients)

					tt_req = TranscodeTarget.TRANSCODE_ALWAYS;

				}else{

					tt_req = job.getTranscodeRequirement();

						// audio hack for PSP audio

					if ( device instanceof TranscodeTarget ){

						if ( provider_analysis.getLongProperty( TranscodeProviderAnalysis.PT_VIDEO_HEIGHT ) == 0 ){

							if (((TranscodeTarget)device).isAudioCompatible( transcode_file )){

								tt_req = TranscodeTarget.TRANSCODE_NEVER;
							}
						}
					}
				}

				if ( tt_req == TranscodeTarget.TRANSCODE_NEVER ){

					xcode_required = false;

				}else if ( tt_req == TranscodeTarget.TRANSCODE_ALWAYS ){

					xcode_required 	= true;

					provider_analysis.setBooleanProperty( TranscodeProviderAnalysis.PT_FORCE_TRANSCODE, true );
				}
			}

			if ( xcode_required ){

				final AESemaphore xcode_sem = new AESemaphore( "xcode:proc" );

				final TranscodeProviderJob[] provider_job = { null };

				TranscodeProviderAdapter xcode_adapter =
					new TranscodeProviderAdapter()
					{
						private boolean	resolution_updated;

						private final int ETA_AVERAGE_SIZE	= 10;

						private int		last_eta;
						private int		eta_samples;
						private Average eta_average 		= AverageFactory.MovingAverage(ETA_AVERAGE_SIZE);
						private int		last_percent;

						private long	initial_file_downloaded 	= job.getFile().getDownloaded();
						private long	file_size				 	= job.getFile().getLength();

						@Override
						public void
						updateProgress(
							int			percent,
							int			eta_secs,
							int			new_width,
							int			new_height )
						{
							last_eta 		= eta_secs;
							last_percent	= percent;

							TranscodeProviderJob	prov_job = provider_job[0];

							if ( prov_job == null ){

								return;
							}

							int	job_state = job.getState();

							if ( 	job_state == TranscodeJob.ST_CANCELLED ||
									job_state == TranscodeJob.ST_REMOVED ){

								prov_job.cancel();

							}else if ( paused || job_state == TranscodeJob.ST_PAUSED ){

								prov_job.pause();

							}else{

								if ( job_state == TranscodeJob.ST_RUNNING ){

									prov_job.resume();
								}

								job.updateProgress( percent, eta_secs );

								prov_job.setMaxBytesPerSecond( max_bytes_per_sec );

								if ( !resolution_updated ){

									if ( new_width > 0 && new_height > 0 ){

										transcode_file.setResolution( new_width, new_height );

										resolution_updated = true;
									}
								}
							}
						}

						@Override
						public void
						streamStats(
							long					connect_rate,
							long					write_speed )
						{
								// problem on OSX with some files thrashing the indirect piped input
								// and eventually failing - try and spot this behaviour and revert
								// to direct input if needed

							if ( 	Constants.isOSX &&
									job.getEnableAutoRetry() &&
									job.canUseDirectInput() &&
									job.getAutoRetryCount() == 0 ){

								if ( connect_rate > 5 && last_percent < 100 ){

									long eta = (long)eta_average.update(last_eta );

									eta_samples++;

									if ( eta_samples >= ETA_AVERAGE_SIZE ){

										long	total_time = (eta*100 )/(100-last_percent);

										long 	total_write = total_time*write_speed;

										DiskManagerFileInfo file = job.getFile();

										long	length = file.getLength();

										if ( length > 0 ){

											double over_write = ((double)total_write)/length;

											if ( over_write > 5.0 ){

												failed( new TranscodeException( "Overwrite limit exceeded, abandoning transcode" ));

												provider_job[0].cancel();
											}
										}
									}
								}else{

									eta_samples = 0;
								}
							}
						}

						@Override
						public void
						failed(
							TranscodeException		e )
						{
							try{
								if ( error[0] == null ){

									error[0] = e;
								}

								if ( e.isRetryDisabled()){

									job.setEnableAutoRetry( false );
								}
							}finally{

								xcode_sem.release();
							}
						}

						@Override
						public void
						complete()
						{
							try{
									// sanity check: for incomplete files at the start of the process ensure that they have completed

								long	current_downloaded = job.getFile().getDownloaded();

								if ( file_size > 0 && initial_file_downloaded < file_size && current_downloaded < file_size ){

									if ( error[0] == null ){

											// actually this ain't so simple as we stream data prior to hash check completion (otherwise for
											// large piece sizes we could be waiting for 4MB to complete downloading before playback)
											// and getDownloaded() only returns the verified data size

										long	contiguous_downloaded = 0;

										try{
											DiskManagerFileInfo	_file_info = job.getFile();

											Download download = _file_info.getDownload();

											com.biglybt.core.disk.DiskManagerFileInfo file_info = PluginCoreUtils.unwrap( _file_info );

											TOTorrentFile torrent_file = file_info.getTorrentFile();

											TOTorrent	torrent = torrent_file.getTorrent();

											TOTorrentFile[]	torrent_files = torrent.getFiles();

											long	byte_start = 0;

											for ( TOTorrentFile tf: torrent_files ){

												if ( tf == torrent_file ){

													break;
												}

												byte_start += tf.getLength();
											}

											DiskManager dm = download.getDiskManager();

											if ( dm == null ){

												throw( new Exception( "Download stopped" ));
											}

											DiskManagerPiece[] pieces = PluginCoreUtils.unwrap( dm ).getPieces();

											long piece_size = torrent.getPieceLength();

											int	first_piece_index 	= (int)( byte_start / piece_size );
											int	first_piece_offset	= (int)( byte_start % piece_size );

											int	last_piece_index	= torrent_file.getLastPieceNumber();

											DiskManagerPiece	first_piece = pieces[first_piece_index];

											if ( !first_piece.isDone()){

												boolean[] blocks = first_piece.getWritten();

												if ( blocks == null ){

													if ( first_piece.isDone()){

														contiguous_downloaded = first_piece.getLength() - first_piece_offset;
													}
												}else{

													int	piece_offset = 0;

													for (int j=0;j<blocks.length;j++){

														if ( blocks[j] ){

															int	block_size = first_piece.getBlockSize( j );

															piece_offset = piece_offset + block_size;

															if ( contiguous_downloaded == 0 ){

																if ( piece_offset > first_piece_offset ){

																	contiguous_downloaded = piece_offset - first_piece_offset;
																}
															}else{

																contiguous_downloaded += block_size;
															}
														}else{

															break;
														}
													}
												}
											}else{

												contiguous_downloaded = first_piece.getLength() - first_piece_offset;

												for (int i=first_piece_index+1;i<=last_piece_index;i++){

													DiskManagerPiece piece = pieces[i];

													if ( piece.isDone()){

														contiguous_downloaded += piece.getLength();

													}else{

														boolean[] blocks = piece.getWritten();

														if ( blocks == null ){

															if ( piece.isDone()){

																contiguous_downloaded += piece.getLength();

															}else{

																break;
															}
														}else{

															for (int j=0;j<blocks.length;j++){

																if ( blocks[j] ){

																	contiguous_downloaded += piece.getBlockSize( j );

																}else{

																	break;
																}
															}
														}

														break;
													}
												}
											}
										}catch( Throwable e ){

											// Debug.out( e );
										}

										if ( contiguous_downloaded < file_size ){

												// things might have improved, check again

											current_downloaded = job.getFile().getDownloaded();

											if ( current_downloaded < file_size ){

												Debug.out( "Premature transcode termination: init=" + initial_file_downloaded + ", curr=" + current_downloaded + ", len=" + file_size );

												error[0] = new TranscodeException( "Transcode terminated prematurely" );
											}
										}
									}
								}
							}finally{

								xcode_sem.release();
							}
						}
					};

				boolean	direct_input = job.useDirectInput();

				if ( job.isStream()){

					/*
					provider_job[0] =
						provider.transcode(
							adapter,
							job.getFile(),
							profile,
							new File( "C:\\temp\\arse").toURI().toURL());
					*/

					pipe = new TranscodePipeStreamSource2(
								new TranscodePipeStreamSource2.streamListener()
								{
									@Override
									public void
									gotStream(
										InputStream is )
									{
										job.setStream( is );
									}
								});

					provider_job[0] =
						provider.transcode(
							xcode_adapter,
							provider_analysis,
							direct_input,
							job.getFile(),
							profile,
							new URL( "tcp://127.0.0.1:" + pipe.getPort()));

				}else{

					File output_file = transcode_file.getCacheFile();

					provider_job[0] =
						provider.transcode(
							xcode_adapter,
							provider_analysis,
							direct_input,
							job.getFile(),
							profile,
							output_file.toURI().toURL());
				}

				provider_job[0].setMaxBytesPerSecond( max_bytes_per_sec );

				TranscodeQueueListener listener =
					new TranscodeQueueListener()
					{
						@Override
						public void
						jobAdded(
							TranscodeJob		job )
						{
						}

						@Override
						public void
						jobChanged(
							TranscodeJob		changed_job )
						{
							if ( changed_job == job ){

								int	state = job.getState();

								if ( state == TranscodeJob.ST_PAUSED ){

									provider_job[0].pause();

								}else if ( state == TranscodeJob.ST_RUNNING ){

									provider_job[0].resume();

								}else if ( 	state == TranscodeJob.ST_CANCELLED ||
											state == TranscodeJob.ST_STOPPED ){

									provider_job[0].cancel();
								}
							}
						}

						@Override
						public void
						jobRemoved(
							TranscodeJob		removed_job )
						{
							if ( removed_job == job ){

								provider_job[0].cancel();
							}
						}
					};

				try{
					addListener( listener );

					xcode_sem.reserve();

				}finally{

					removeListener( listener );
				}

				if ( error[0] != null ){

					throw( error[0] );
				}
			}else{

					// no transcode required...

				DiskManagerFileInfo source = job.getFile();

				transcode_file.setTranscodeRequired( false );

				if ( job.isStream()){

					PluginInterface av_pi = PluginInitializer.getDefaultInterface().getPluginManager().getPluginInterfaceByID( "azupnpav" );

					if ( av_pi == null ){

						throw( new TranscodeException( "Media Server plugin not found" ));
					}

					IPCInterface av_ipc = av_pi.getIPC();

					String url_str = (String)av_ipc.invoke( "getContentURL", new Object[]{ source });


					if ( url_str == null || url_str.length() == 0 ){

							// see if we can use the file directly

						File source_file = source.getFile();

						if ( source_file.exists()){

							job.setStream( new BufferedInputStream( new FileInputStream( source_file )));

						}else{

							throw( new TranscodeException( "No UPnPAV URL and file doesn't exist" ));
						}
					}else{

						URL source_url = new URL( url_str );

						job.setStream( source_url.openConnection().getInputStream());
					}
				}else{
					boolean url_input_source = source instanceof DiskManagerFileInfoURL;

					if ( device.getAlwaysCacheFiles() || url_input_source ){

						PluginInterface av_pi = PluginInitializer.getDefaultInterface().getPluginManager().getPluginInterfaceByID( "azupnpav" );

						if ( av_pi == null ){

							throw( new TranscodeException( "Media Server plugin not found" ));
						}

						IPCInterface av_ipc = av_pi.getIPC();

						String url_str = (String)av_ipc.invoke( "getContentURL", new Object[]{ source });

						InputStream	is;

						long		length;

						if ( url_str == null || url_str.length() == 0 ){

								// see if we can use the file directly

							if ( url_input_source ){

								((DiskManagerFileInfoURL)source).download();
							}

							File source_file = source.getFile();

							if ( source_file.exists()){

								is = new BufferedInputStream( new FileInputStream( source_file ));

								length = source_file.length();

							}else{

								throw( new TranscodeException( "No UPnPAV URL and file doesn't exist" ));
							}
						}else{

							URL source_url = new URL( url_str );

							URLConnection connection = source_url.openConnection();

							is = source_url.openConnection().getInputStream();

							String s = connection.getHeaderField( "content-length" );

							if ( s != null ){

								length = Long.parseLong( s );

							}else{

								length = -1;
							}
						}

						OutputStream	os = null;

						final boolean[]	cancel_copy = { false };

						TranscodeQueueListener copy_listener =
							new TranscodeQueueListener()
							{
								@Override
								public void
								jobAdded(
									TranscodeJob		job )
								{
								}

								@Override
								public void
								jobChanged(
									TranscodeJob		changed_job )
								{
									if ( changed_job == job ){

										int	state = job.getState();

										if ( state == TranscodeJob.ST_PAUSED ){

										}else if ( state == TranscodeJob.ST_RUNNING ){

										}else if ( 	state == TranscodeJob.ST_CANCELLED ||
													state == TranscodeJob.ST_STOPPED ){

											cancel_copy[0] = true;
										}
									}
								}

								@Override
								public void
								jobRemoved(
									TranscodeJob		removed_job )
								{
									if ( removed_job == job ){

										cancel_copy[0] = true;
									}
								}
							};

						try{
							addListener( copy_listener );

							os = new FileOutputStream( transcode_file.getCacheFile());

							long	total_copied = 0;

							byte[] buffer = new byte[128*1024];

							while( true ){

								if ( cancel_copy[0] ){

									throw( new TranscodeException( "Copy cancelled" ));
								}

								int	len = is.read( buffer );

								if ( len <= 0 ){

									break;
								}

								os.write( buffer, 0, len );

								total_copied += len;

								if ( length > 0 ){

									job.updateProgress((int)( total_copied*100/length ), -1 );
								}

								total_copied += len;
							}
						}finally{

							try{
								is.close();

							}catch( Throwable e ){

								Debug.out( e );
							}

							try{
								if ( os != null ){

									os.close();
								}
							}catch( Throwable e ){

								Debug.out( e );
							}

							removeListener( copy_listener );
						}
					}
				}
			}

			job.complete();

			return( true );

		}catch( Throwable e ){

			job.failed( e );

			e.printStackTrace();

			if ( 	!job.isStream() &&
					job.getEnableAutoRetry() &&
					job.getAutoRetryCount() == 0 &&
					job.canUseDirectInput() &&
					!job.useDirectInput()){

				log( "Auto-retrying transcode with direct input" );

				job.setUseDirectInput();

				job.setAutoRetry( true );

				queue_sem.release();
			}

			return( false );

		}finally{

			if ( pipe != null ){

				pipe.destroy();
			}

			device.setTranscoding( false );

			current_job = null;
		}
	}

	protected void
	schedule()
	{
		synchronized( this ){

			if ( queue.size() > 0 && queue_thread == null ){

				queue_thread = new
					AEThread2( "XcodeQ", true )
					{
						@Override
						public void
						run()
						{
							try{
								while( true ){

									checkJobStatus();

									boolean got = queue_sem.reserve( 30*1000 );

									TranscodeJobImpl	job = null;

									synchronized( TranscodeQueueImpl.this ){

										if ( !got ){

											if ( queue.size() == 0 ){

												queue_thread = null;

												return;
											}
										}

										for ( TranscodeJobImpl j: queue ){

											int state = j.getState();

												// pick up any existing paused ones (remember, paused=running but with transcode paused

											if ( state == TranscodeJob.ST_FAILED && j.isAutoRetry()){

												j.setAutoRetry( false );

												j.reset();

												job	= j;

												break;

											}else if ( state == TranscodeJob.ST_PAUSED ){

													// remember paused here is paused after an azureus
													// restart as 'process' blocks on pause

												job = j;

											}else if ( state == TranscodeJob.ST_QUEUED ){

												if ( job == null && (j.getDownloadETA() == 0 || j.getTranscodeRequirement() == TranscodeTarget.TRANSCODE_NEVER) ){

													job = j;
												}
											}
										}
									}

									checkJobStatus();

									if ( job != null ){

										if ( process( job )){

											try{
												remove( job, true );

											}catch( TranscodeActionVetoException e ){

												Debug.out( e );
											}
										}
									}
								}
							}finally{

								checkJobStatus();
							}
						}
					};

				queue_thread.start();
			}
		}
	}

	protected void
	updateStatus(
		int	tick_count )
	{
		int queue_size;

		synchronized( this ){

			queue_size = queue.size();
		}

		if ( queue_size > 0 ){

			TranscodeJobImpl[] jobs = getJobs();

			for ( TranscodeJobImpl job: jobs ){

				job.updateStatus();
			}
		}
	}

	protected void
	checkJobStatus()
	{
		Set<DeviceImpl> devices = new HashSet<>(Arrays.asList(manager.getManager().getDevices()));

		synchronized( this ){

			for ( TranscodeJobImpl j: queue ){

				if ( j.getState() == TranscodeJob.ST_FAILED ){

					DeviceImpl	device = j.getDevice();

					device.setError( KEY_XCODE_ERROR, MessageText.getString( "device.error.xcodefail" ));

					devices.remove( device );
				}
			}
		}

		for ( DeviceImpl device: devices ){

			device.setError( KEY_XCODE_ERROR, null );
		}
	}

	@Override
	public TranscodeJobImpl
	add(
		TranscodeTarget			target,
		TranscodeProfile		profile,
		DiskManagerFileInfo		file,
		boolean					add_stopped )

		throws TranscodeException
	{
		return( add( target, profile, file, add_stopped, false, TranscodeTarget.TRANSCODE_UNKNOWN ));
	}

	@Override
	public TranscodeJobImpl
	add(
		TranscodeTarget			target,
		TranscodeProfile		profile,
		DiskManagerFileInfo		file,
		int						transcode_requirement,
		boolean					add_stopped )

		throws TranscodeException
	{
		return( add( target, profile, file, add_stopped, false, transcode_requirement ));
	}

	public TranscodeJobImpl
	add(
		TranscodeTarget			target,
		TranscodeProfile		profile,
		DiskManagerFileInfo		file,
		boolean					add_stopped,
		boolean					stream,
		int						transcode_requirement )

		throws TranscodeException
	{
		TranscodeFileImpl existing_tf = ((DeviceImpl)target.getDevice()).lookupFile( profile, file );

		if ( existing_tf != null ){

			List<TranscodeJobImpl>	to_remove = new ArrayList<>();

			synchronized( this ){

				for ( TranscodeJobImpl job: queue ){

					if ( job.getTarget() == target && job.getTranscodeFile().equals( existing_tf )){

						to_remove.add( job );
					}
				}
			}

			for ( TranscodeJobImpl job: to_remove ){

				job.removeForce();
			}

			if ( !stream ){

				existing_tf.delete( true );
			}
		}

		TranscodeJobImpl job = new TranscodeJobImpl( this, target, profile, file, add_stopped, transcode_requirement, stream );

		try{
			synchronized( this ){

				queue.add( job );

				queue_sem.release();

				configDirty();
			}

			for ( TranscodeQueueListener listener: listeners ){

				try{
					listener.jobAdded( job );

				}catch( Throwable e ){

					Debug.printStackTrace( e );
				}
			}
		}finally{

			schedule();
		}

		return( job );
	}

	protected void
	remove(
		TranscodeJobImpl		job,
		boolean					force )

		throws TranscodeActionVetoException
	{
		synchronized( this ){

			if ( !queue.contains( job )){

				return;
			}
		}

		if ( !force ){

			for ( TranscodeQueueActionListener l: action_listeners ){

				try{
					l.jobWillBeActioned( job, TranscodeQueueActionListener.ACT_REMOVE );

				}catch( TranscodeActionVetoException e ){

					throw( e );

				}catch( Throwable e ){

					Debug.out( e);
				}
			}
		}

		synchronized( this ){

			if ( !queue.remove( job )){

				return;
			}
		}

		configDirty();

		job.destroy();

		for ( TranscodeQueueListener listener: listeners ){

			try{
				listener.jobRemoved( job );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}

		checkJobStatus();

		schedule();
	}

	protected void
	jobChanged(
		TranscodeJob			job,
		boolean					schedule,
		boolean					persistable )
	{

		for ( TranscodeQueueListener listener: listeners ){

			try{
				listener.jobChanged( job );

			}catch( Throwable e ){

				Debug.printStackTrace( e );
			}
		}

		if ( persistable ){

			configDirty();
		}

		if ( schedule ){

			queue_sem.release();

			schedule();
		}
	}

	protected int
	getIndex(
		TranscodeJobImpl		job )
	{
		synchronized( this ){

			return( queue.indexOf(job)+1);
		}
	}

	@Override
	public TranscodeJobImpl[]
	getJobs()
	{
		synchronized( this ){

			return( queue.toArray( new TranscodeJobImpl[queue.size()]));
		}
	}

	@Override
	public int
	getJobCount()
	{
		synchronized( this ){

			return( queue.size());
		}
	}

	@Override
	public TranscodeJob
	getCurrentJob()
	{
		return( current_job );
	}

	@Override
	public boolean
	isTranscoding()
	{
		return( current_job != null );
	}

	protected TranscodeJobImpl
	getJob(
		TranscodeFile		for_file )
	{
		synchronized( this ){

			for ( TranscodeJobImpl job: queue ){

				if ( job.getTranscodeFile().equals( for_file )){

					return( job );
				}
			}
		}

		return( null );
	}

	public void
	moveUp(
		TranscodeJobImpl	job )
	{
		TranscodeJob[] updated;

		synchronized( this ){

			int index = queue.indexOf( job );

			if ( index <= 0 || queue.size() == 1 ){

				return;
			}

			queue.remove( job );

			queue.add( index-1, job );

			updated = getJobs();
		}

		for ( TranscodeJob j: updated ){

			jobChanged( j, false, true );
		}
	}

	public void
	moveDown(
		TranscodeJobImpl	job )
	{
		TranscodeJob[] updated;

		synchronized( this ){

			int index = queue.indexOf( job );

			if ( index < 0 || index == queue.size() - 1 ){

				return;
			}

			queue.remove( job );

			queue.add( index+1, job );

			updated = getJobs();
		}

		for ( TranscodeJob j: updated ){

			jobChanged( j, false, true );
		}
	}

	@Override
	public void
	pause()
	{
		if ( !paused ){

			if ( paused ){

				COConfigurationManager.setParameter( "xcode.paused", true );
			}
		}
	}

	@Override
	public boolean
	isPaused()
	{
		return( paused );
	}

	@Override
	public void
	resume()
	{
		if ( paused ){

			COConfigurationManager.setParameter( "xcode.queue.paused", false );
		}
	}

	@Override
	public long
	getMaxBytesPerSecond()
	{
		return( max_bytes_per_sec );
	}

	@Override
	public void
	setMaxBytesPerSecond(
		long		max )
	{
		COConfigurationManager.setParameter( "xcode.queue.maxbps", max );
	}

	protected TranscodeTarget
	lookupTarget(
		String		target_id )

		throws TranscodeException
	{
		return( manager.lookupTarget( target_id ));
	}

	protected TranscodeProfile
	lookupProfile(
		String		profile_id )

		throws TranscodeException
	{
		TranscodeProfile profile = manager.getProfileFromUID( profile_id );

		if ( profile == null ){

			throw( new TranscodeException( "Transcode profile with id '" + profile_id + "' not found" ));
		}

		return( profile );
	}

	protected DiskManagerFileInfo
	lookupFile(
		byte[]		hash,
		int			index )

		throws TranscodeException
	{
		return( manager.lookupFile( hash, index ));
	}

	protected void
	analyse(
		final TranscodeJobImpl			job,
		final TranscodeAnalysisListener	listener )

		throws TranscodeException
	{
		anaylsis_dispatcher.dispatch(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					try{
						TranscodeProviderAnalysis analysis = analyse( job );

						listener.analysisComplete( job, analysis );

					}catch( TranscodeException e ){

						listener.analysisFailed( job, e );

					}catch( Throwable e ){

						listener.analysisFailed( job, new TranscodeException( "Analysis failed", e ));
					}
				}
			});
	}

	protected TranscodeProviderAnalysis
	analyse(
		final TranscodeJobImpl			job )

		throws TranscodeException
	{
		TranscodeProvider provider = job.getProfile().getProvider();

		if ( provider == null ){

			throw( new TranscodeException( "Transcode provider not available" ));
		}

		final TranscodeException[] error = { null };

		TranscodeProfile profile = job.getProfile();

		final AESemaphore analysis_sem = new AESemaphore( "analysis:proc" );

		final boolean	was_stopped = job.getState() == TranscodeJob.ST_STOPPED;

		TranscodeProviderAdapter analysis_adapter =
			new TranscodeProviderAdapter()
			{
				@Override
				public void
				updateProgress(
					int		percent,
					int		eta_secs,
					int		width,
					int		height )
				{
				}

				@Override
				public void
				streamStats(
					long					connect_rate,
					long					write_speed )
				{
				}

				@Override
				public void
				failed(
					TranscodeException		e )
				{
					error[0] = e;

					analysis_sem.release();
				}

				@Override
				public void
				complete()
				{
					analysis_sem.release();
				}
			};

		final TranscodeProviderAnalysis provider_analysis =	provider.analyse( analysis_adapter, job.getFile(), profile );

		TranscodeQueueListener analysis_q_listener =
			new TranscodeQueueListener()
			{
				@Override
				public void
				jobAdded(
					TranscodeJob		job )
				{
				}

				@Override
				public void
				jobChanged(
					TranscodeJob		changed_job )
				{
					if ( changed_job == job ){

						int	state = job.getState();

						if ( 	state == TranscodeJob.ST_CANCELLED ){

							provider_analysis.cancel();

						}else if ( 	state == TranscodeJob.ST_STOPPED ){

								// only treat this as a failure if the job was initially
								// running and has been explicitly stopped

							if ( !was_stopped ){

								provider_analysis.cancel();
							}
						}
					}
				}

				@Override
				public void
				jobRemoved(
					TranscodeJob		removed_job )
				{
					if ( removed_job == job ){

						provider_analysis.cancel();
					}
				}
			};

		try{
			addListener( analysis_q_listener );

			analysis_sem.reserve();

		}finally{

			removeListener( analysis_q_listener );
		}

		if ( error[0] != null ){

			throw( error[0] );
		}

		TranscodeFileImpl		transcode_file = job.getTranscodeFile();

		transcode_file.update( provider_analysis );

		return( provider_analysis );
	}

	protected void
	configDirty()
	{
		synchronized( this ){

			if ( config_dirty ){

				return;
			}

			config_dirty = true;

			new DelayedEvent(
				"TranscodeQueue:save", 5000,
				new AERunnable()
				{
					@Override
					public void
					runSupport()
					{
						synchronized( TranscodeQueueImpl.this ){

							if ( !config_dirty ){

								return;
							}

							saveConfig();
						}
					}
				});
		}
	}

	protected void
	loadConfig()
	{
		if ( !FileUtil.resilientConfigFileExists( CONFIG_FILE )){

			return;
		}

		log( "Loading configuration" );

		try{
			synchronized( this ){

				Map map = FileUtil.readResilientConfigFile( CONFIG_FILE );

				List<Map<String,Object>>	l_jobs = (List<Map<String,Object>>)map.get( "jobs" );

				if ( l_jobs != null ){

					for ( Map<String,Object> m: l_jobs ){

						try{
							TranscodeJobImpl job = new TranscodeJobImpl( this, m );

							queue.add(job );

							queue_sem.release();

						}catch( Throwable e ){

							log( "Failed to restore job: " + m, e );
						}
					}
				}
			}
		}catch( Throwable e ){

			log( "Configuration load failed", e );
		}
	}

	protected void
	saveConfig()
	{
		synchronized( this ){

			config_dirty = false;

			if ( queue.size() == 0 ){

				FileUtil.deleteResilientConfigFile( CONFIG_FILE );

			}else{

				Map<String,Object>	map = new HashMap<>();

				List<Map<String,Object>>	l_jobs = new ArrayList<>();

				map.put( "jobs", l_jobs );

				for ( TranscodeJobImpl job: queue ){

					if ( job.isStream()){

						continue;
					}

					try{

						l_jobs.add( job.toMap());

					}catch( Throwable e ){

						log( "Failed to save job", e );
					}
				}

				FileUtil.writeResilientConfigFile( CONFIG_FILE, map );
			}
		}
	}

	protected void
	close()
	{
		if ( config_dirty ){

			saveConfig();
		}
	}

	@Override
	public void
	addListener(
		TranscodeQueueListener		listener )
	{
		if (!listeners.contains(listener)) {
			listeners.add( listener );
		}
	}

	@Override
	public void
	removeListener(
		TranscodeQueueListener		listener )
	{
		listeners.remove( listener );
	}

	@Override
	public void
	addActionListener(
		TranscodeQueueActionListener		listener )
	{
		action_listeners.add( listener );
	}

	@Override
	public void
	removeActionListener(
		TranscodeQueueActionListener		listener )
	{
		action_listeners.remove( listener );
	}

	protected void
	log(
		String	str )
	{
		manager.log( "Queue: " + str );
	}

	protected void
	log(
		String		str,
		Throwable	e )
	{
		manager.log( "Queue: " + str, e );
	}

	public void
	generate(
		IndentWriter		writer )
	{
		writer.println( "Transcode Queue: paused=" + paused + ",max_bps=" + max_bytes_per_sec );

		try{
			writer.indent();

			TranscodeJobImpl[] jobs = getJobs();

			for ( TranscodeJobImpl job: jobs ){

				job.generate( writer );
			}
		}finally{

			writer.exdent();
		}
	}
}
