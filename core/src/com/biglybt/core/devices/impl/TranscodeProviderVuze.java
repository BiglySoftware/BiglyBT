/*
 * Created on Feb 5, 2009
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

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.biglybt.core.devices.*;
import com.biglybt.core.download.DiskManagerFileInfoURL;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.AEThread2;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.UrlUtils;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.disk.DiskManagerFileInfo;
import com.biglybt.pif.ipc.IPCException;
import com.biglybt.pif.ipc.IPCInterface;

public class
TranscodeProviderVuze
	implements TranscodeProvider
{
	private static final String PROFILE_PREFIX = "vuzexcode:";

	private TranscodeManagerImpl	manager;
	private PluginInterface			plugin_interface;

	private volatile TranscodeProfile[]		profiles;
	private Map<String,TranscodeProfile[]>	profile_classification_map = new HashMap<>();

	protected
	TranscodeProviderVuze(
		TranscodeManagerImpl	_manager,
		PluginInterface			_plugin_interface )
	{
		manager					= _manager;

		update(_plugin_interface);
	}

	@Override
	public int
	getID()
	{
		return( TP_VUZE );
	}

	public PluginInterface
	getPluginInterface()
	{
		return( plugin_interface );
	}

	protected void
	update(
		PluginInterface		pi )
	{
		plugin_interface		= pi;

		try {
			plugin_interface.getIPC().invoke("addProfileListChangedListener",
					new Object[] {
						new AERunnable() {
							@Override
							public void runSupport() {

								resetProfiles();
							}
						}
					});
		} catch (IPCException e) {
		}

		resetProfiles();
	}

	@Override
	public String
	getName()
	{
		return( plugin_interface.getPluginName() + ": version=" + plugin_interface.getPluginVersion());
	}

	void
	resetProfiles()
	{
		synchronized( profile_classification_map ){

			profile_classification_map.clear();

			profiles = null;
		}
	}

	@Override
	public TranscodeProfile[]
	getProfiles()
	{
		if ( profiles != null ){

			return( profiles );
		}

		try{
			Map<String, Map<String,Object>> profiles_map = (Map<String,Map<String,Object>>)plugin_interface.getIPC().invoke( "getProfiles", new Object[]{} );

			TranscodeProfile[] res = new TranscodeProfile[profiles_map.size()];

			int	index = 0;

			for ( Map.Entry<String, Map<String,Object>> entry : profiles_map.entrySet()){

				res[ index++] = new TranscodeProfileImpl( manager, TP_VUZE, PROFILE_PREFIX + entry.getKey(), entry.getKey(), entry.getValue());
			}

			profiles	= res;

			return( res );

		}catch( Throwable e ){

			e.printStackTrace();

			return( new TranscodeProfile[0] );
		}
	}

	@Override
	public TranscodeProfile[]
	getProfiles(
		String classification_prefix )
	{
		classification_prefix = classification_prefix.toLowerCase();

		TranscodeProfile[] profs = getProfiles();

		synchronized( profile_classification_map ){

			TranscodeProfile[] 	res = profile_classification_map.get( classification_prefix );

			if ( res != null ){

				return( res );
			}
		}

		List<TranscodeProfile> c_profiles = new ArrayList<>();

		for ( TranscodeProfile p : profs ){

			String c = p.getDeviceClassification();

			if ( c == null ){

				manager.log( "Device classification missing for " + p.getName());

			}else{

				if ( c.toLowerCase().startsWith( classification_prefix )){

					c_profiles.add( p );
				}
			}
		}

		TranscodeProfile[] res = c_profiles.toArray( new TranscodeProfile[ c_profiles.size()]);

		synchronized( profile_classification_map ){

			if ( profs == profiles ){

				profile_classification_map.put( classification_prefix, res );
			}
		}

		return( res );
	}

	@Override
	public TranscodeProfile
	getProfile(
		String		UID )
	{
		TranscodeProfile[] profiles = getProfiles();

		for ( TranscodeProfile profile: profiles ){

			if ( profile.getUID().equals( UID )){

				return( profile );
			}
		}

		return( null );
	}

	@Override
	public TranscodeProfile
	addProfile(
		File 		file )

		throws TranscodeException
	{
		try{
			String uid = PROFILE_PREFIX + (String)plugin_interface.getIPC().invoke( "addProfile", new Object[]{ file } );

			resetProfiles();

			return( getProfile( uid ));

		}catch( Throwable e ){

			throw( new TranscodeException( "Failed to add profile", e ));
		}
	}

	@Override
	public TranscodeProviderAnalysis
	analyse(
		final TranscodeProviderAdapter	_adapter,
		DiskManagerFileInfo				input,
		TranscodeProfile				profile )

		throws TranscodeException
	{
		try{

			URL 				source_url		= null;
			File				source_file	 	= null;

			long	input_length = input.getLength();

			if ( input_length > 0 && input_length == input.getDownloaded()){

				File file = input.getFile();

				if ( file.exists() && file.length() == input_length ){

					source_file = file;
				}
			}

			TranscodePipe		pipe = null;

			if ( source_file == null ){

				if ( input instanceof DiskManagerFileInfoURL ){

					source_url = ((DiskManagerFileInfoURL)input).getURL();

				}else{
						// race condition here on auto-transcodes due to downloadadded listeners - can add the xcode to queue
						// and schedule before added to upnpms - simple hack is to hang about a bit

					for ( int i=0; i<10; i++ ){

						PluginInterface av_pi = plugin_interface.getPluginManager().getPluginInterfaceByID( "azupnpav" );

						if ( av_pi == null ){

							throw( new TranscodeException( "Media Server plugin not found" ));
						}

						IPCInterface av_ipc = av_pi.getIPC();

						String url_str = (String)av_ipc.invoke( "getContentURL", new Object[]{ input });

						if ( url_str != null && url_str.length() > 0 ){

							source_url = new URL( url_str );

							pipe = new TranscodePipeStreamSource( source_url.getHost(), source_url.getPort());

							source_url = UrlUtils.setHost( source_url, "127.0.0.1" );

							source_url = UrlUtils.setPort( source_url, pipe.getPort());
						}

						if ( source_url != null ){

							break;

						}else{

							try{
								Thread.sleep(1000);

							}catch( Throwable e ){

								break;
							}
						}
					}
				}
			}

			if ( source_file == null && source_url == null ){

				throw( new TranscodeException( "File doesn't exist" ));
			}

			final TranscodePipe f_pipe = pipe;

			try{
				final IPCInterface	ipc = plugin_interface.getIPC();

				final Object analysis_context;

				if ( source_url != null ){

					analysis_context = ipc.invoke(
						"analyseContent",
						new Object[]{
							source_url,
							profile.getName() });
				}else{

					analysis_context = ipc.invoke(
						"analyseContent",
						new Object[]{
							source_file,
							profile.getName() });
				}

				final Map<String,Object>	result = new HashMap<>();

				final TranscodeProviderAnalysisImpl analysis =
					new TranscodeProviderAnalysisImpl()
					{
						@Override
						public void
						cancel()
						{
							try{
								ipc.invoke( "cancelAnalysis", new Object[]{ analysis_context });

							}catch( Throwable e ){

								Debug.printStackTrace( e );
							}
						}

						@Override
						public boolean
						foundVideoStream()
						{
							return( getLongProperty( PT_VIDEO_WIDTH ) > 0 );
						}

						@Override
						public boolean
						getBooleanProperty(
							int		property )
						{
							if ( property == PT_TRANSCODE_REQUIRED ){

								return( getBooleanProperty( "xcode_required", true ));

							}else{

								Debug.out( "Unknown property: " + property );

								return( false );
							}
						}

						@Override
						public void
						setBooleanProperty(
							int		property,
							boolean	value )
						{
							if ( property == PT_FORCE_TRANSCODE ){

								result.put( "force_xcode", value );

							}else{

								Debug.out( "Unknown property: " + property );
							}
						}

						@Override
						public long
						getLongProperty(
							int		property )
						{
							if ( property == PT_DURATION_MILLIS ){

								long duration = getLongProperty( "duration_millis", 0 );

								long audio_duration	= getLongProperty( "audio_duration_millis", 0 );

								if ( duration <= 0 && audio_duration > 0 ){

									duration = audio_duration;
								}

								if ( audio_duration > 0 && audio_duration < duration ){

									duration = audio_duration;
								}

								return( duration );

							}else if ( property == PT_VIDEO_WIDTH ){

								return( getLongProperty( "video_width", 0 ));

							}else if ( property == PT_VIDEO_HEIGHT ){

								return( getLongProperty( "video_height", 0 ));

							}else if ( property == PT_SOURCE_SIZE ){

								return( getLongProperty( "source_size", 0 ));

							}else if ( property == PT_ESTIMATED_XCODE_SIZE ){

								return( getLongProperty( "estimated_transcoded_size", 0 ));

							}else{

								Debug.out( "Unknown property: " + property );

								return( 0 );
							}
						}

						protected boolean
						getBooleanProperty(
							String		name,
							boolean		def )
						{
							Boolean b = (Boolean)result.get( name );

							if ( b != null ){

								return( b );
							}

							return( def );
						}

						protected long
						getLongProperty(
							String		name,
							long		def )
						{
							Long l = (Long)result.get( name );

							if ( l != null ){

								return( l );
							}

							return( def );
						}

						@Override
						public Map<String,Object>
						getResult()
						{
							return( result );
						}
					};

				new AEThread2( "analysisStatus", true )
				{
					@Override
					public void
					run()
					{
						try{
							while( true ){

								try{
									Map status = (Map)ipc.invoke( "getAnalysisStatus", new Object[]{ analysis_context });

									long	state = (Long)status.get( "state" );

									if ( state == 0 ){

											// running

										Thread.sleep(50);

									}else if ( state == 1 ){

										_adapter.failed( new TranscodeException( "Analysis cancelled" ));

										break;

									}else if ( state == 2 ){

										_adapter.failed( new TranscodeException( "Analysis failed", (Throwable)status.get( "error" )));

										break;

									}else{

										result.putAll((Map<String,Object>)status.get( "result" ));

										_adapter.complete();

										break;
									}
								}catch( Throwable e ){

									_adapter.failed( new TranscodeException( "Failed to get status", e ));

									break;
								}
							}
						}finally{

							if ( f_pipe != null ){

								f_pipe.destroy();
							}
						}
					}
				}.start();

				return( analysis );


			}catch( Throwable e ){

				if ( pipe != null ){

					pipe.destroy();
				}

				throw( e );
			}
		}catch( TranscodeException e ){

			throw( e );

		}catch( Throwable e ){

			throw( new TranscodeException( "analysis failed", e ));
		}
	}

	@Override
	public TranscodeProviderJob
	transcode(
		final TranscodeProviderAdapter	_adapter,
		TranscodeProviderAnalysis		analysis,
		boolean							direct_input,
		DiskManagerFileInfo				input,
		TranscodeProfile				profile,
		URL								output )

		throws TranscodeException
	{
		try{
			PluginInterface av_pi = plugin_interface.getPluginManager().getPluginInterfaceByID( "azupnpav" );

			if ( av_pi == null ){

				throw( new TranscodeException( "Media Server plugin not found" ));
			}

			final TranscodeProviderJob[] xcode_job = { null };

			URL 				source_url	= null;
			TranscodePipe		pipe		= null;

			if ( direct_input ){

				if ( input instanceof DiskManagerFileInfoURL ){

					((DiskManagerFileInfoURL)input).download();
				}

				if ( input.getDownloaded() == input.getLength()){

					File	file = input.getFile();

					if ( file.exists() && file.length() == input.getLength()){

						source_url = file.toURI().toURL();
					}
				}

				if ( source_url == null ){

					manager.log( "Failed to use direct input as source file doesn't exist/incomplete" );
				}
			}

			if ( source_url == null ){

				if ( input instanceof DiskManagerFileInfoURL ){

					source_url = ((DiskManagerFileInfoURL)input).getURL();

				}else{

					IPCInterface av_ipc = av_pi.getIPC();

					String url_str = (String)av_ipc.invoke( "getContentURL", new Object[]{ input });


					if ( url_str == null || url_str.length() == 0 ){

							// see if we can use the file directly

						File source_file = input.getFile();

						if ( source_file.exists()){

							pipe =
								new TranscodePipeFileSource(
										source_file,
										new TranscodePipe.errorListener()
										{
											@Override
											public void
											error(
												Throwable e )
											{
												_adapter.failed(
													new TranscodeException( "File access error", e ));

												if ( xcode_job[0] != null ){

													xcode_job[0].cancel();
												}
											}
										});

							source_url = new URL( "http://127.0.0.1:" + pipe.getPort() + "/" );

						}else{

							throw( new TranscodeException( "Source file doesn't exist" ));
						}
					}else{

						source_url = new URL( url_str );

						pipe = new TranscodePipeStreamSource( source_url.getHost(), source_url.getPort());

						source_url = UrlUtils.setHost( source_url, "127.0.0.1" );

						source_url = UrlUtils.setPort( source_url, pipe.getPort());
					}
				}
			}

			final TranscodePipe f_pipe = pipe;

			try{
				final IPCInterface	ipc = plugin_interface.getIPC();

				final Object context;

				final TranscodeProviderAdapter	adapter;

				if ( output.getProtocol().equals( "tcp" )){

					adapter = _adapter;

					context =
						ipc.invoke(
							"transcodeToTCP",
							new Object[]{
								((TranscodeProviderAnalysisImpl)analysis).getResult(),
								source_url,
								profile.getName(),
								output.getPort() });
				}else{

					final File file = new File( output.toURI());

					File	parent_dir = file.getParentFile();

					if ( parent_dir.exists()){

						if ( !parent_dir.canWrite()){

							throw( new TranscodeException( "Folder '" + parent_dir.getAbsolutePath() + "' isn't writable" ));
						}
					}else{

						if ( !parent_dir.mkdirs()){

							throw( new TranscodeException( "Failed to create folder '" + parent_dir.getAbsolutePath() + "'" ));
						}
					}

					adapter =
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
								_adapter.updateProgress( percent, eta_secs, width, height );
							}

							@Override
							public void
							streamStats(
								long					connect_rate,
								long					write_speed )
							{
								_adapter.streamStats(connect_rate, write_speed);
							}

							@Override
							public void
							failed(
								TranscodeException		error )
							{
								try{
									file.delete();

								}finally{

									_adapter.failed( error );
								}
							}

							@Override
							public void
							complete()
							{
								_adapter.complete();
							}
						};

					context =
						ipc.invoke(
							"transcodeToFile",
							new Object[]{
								((TranscodeProviderAnalysisImpl)analysis).getResult(),
								source_url,
								profile.getName(),
								file });
				}

				new AEThread2( "xcodeStatus", true )
					{
						@Override
						public void
						run()
						{
							try{
								boolean	in_progress = true;

								while( in_progress ){

									in_progress = false;

									if ( f_pipe != null ){

										adapter.streamStats( f_pipe.getConnectionRate(), f_pipe.getWriteSpeed());
									}

									try{
										Map status = (Map)ipc.invoke( "getTranscodeStatus", new Object[]{ context });

										long	state = (Long)status.get( "state" );

										if ( state == 0 ){

											int	percent = (Integer)status.get( "percent" );

											Integer	i_eta	= (Integer)status.get( "eta_secs" );

											int eta = i_eta==null?-1:i_eta;

											Integer	i_width	= (Integer)status.get( "new_width" );

											int width = i_width==null?0:i_width;

											Integer	i_height	= (Integer)status.get( "new_height" );

											int height = i_height==null?0:i_height;

											adapter.updateProgress( percent, eta, width, height );

											if ( percent == 100 ){

												adapter.complete();

											}else{

												in_progress = true;

												Thread.sleep(1000);
											}
										}else if ( state == 1 ){

											adapter.failed( new TranscodeException( "Transcode cancelled" ));

										}else{

											Boolean	perm_fail = (Boolean)status.get( "error_is_perm" );

											TranscodeException error = new TranscodeException( "Transcode failed", (Throwable)status.get( "error" ));

											if ( perm_fail != null && perm_fail ){

												error.setDisableRetry( true );
											}

											adapter.failed( error );
										}
									}catch( Throwable e ){

										adapter.failed( new TranscodeException( "Failed to get status", e ));
									}
								}
							}finally{

								if ( f_pipe != null ){

									f_pipe.destroy();
								}
							}
						}
					}.start();


				xcode_job[0] =
					new TranscodeProviderJob()
					{
						@Override
						public void
						pause()
						{
							if ( f_pipe != null ){

								f_pipe.pause();
							}
						}

						@Override
						public void
						resume()
						{
							if ( f_pipe != null ){

								f_pipe.resume();
							}
						}

						@Override
						public void
						cancel()
						{
							try{
								ipc.invoke( "cancelTranscode", new Object[]{ context });

							}catch( Throwable e ){

								Debug.printStackTrace( e );
							}
						}

						@Override
						public void
						setMaxBytesPerSecond(
							int		 max )
						{
							if ( f_pipe != null ){

								f_pipe.setMaxBytesPerSecond( max );
							}
						}
					};

				return( xcode_job[0] );

			}catch( Throwable e ){

				if ( pipe != null ){

					pipe.destroy();
				}

				throw( e );
			}
		}catch( TranscodeException e ){

			throw( e );

		}catch( Throwable e ){

			throw( new TranscodeException( "transcode failed", e ));
		}
	}

	@Override
	public File
	getAssetDirectory()
	{
		File file = plugin_interface.getPluginconfig().getPluginUserFile( "assets" );

		if ( !file.exists()){

			file.mkdirs();
		}

		return( file );
	}

	protected void
	destroy()
	{
		// TODO
	}

	protected interface
	TranscodeProviderAnalysisImpl
		extends TranscodeProviderAnalysis
	{
		public Map<String,Object>
		getResult();
	}
}
