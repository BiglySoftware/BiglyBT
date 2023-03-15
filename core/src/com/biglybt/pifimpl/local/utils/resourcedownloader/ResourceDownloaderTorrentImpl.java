/*
 * Created on 21-May-2004
 * Created by Paul Gardner
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

package com.biglybt.pifimpl.local.utils.resourcedownloader;

/**
 * @author parg
 *
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.tag.Tag;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.tag.TagType;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.torrent.TOTorrentException;
import com.biglybt.core.torrent.TOTorrentFactory;
import com.biglybt.core.util.*;
import com.biglybt.pif.download.*;
import com.biglybt.pif.torrent.Torrent;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloader;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderCancelledException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderException;
import com.biglybt.pif.utils.resourcedownloader.ResourceDownloaderListener;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.pifimpl.local.torrent.TorrentImpl;
import com.biglybt.plugin.I2PHelpers;

public class
ResourceDownloaderTorrentImpl
	extends 	ResourceDownloaderBaseImpl
	implements	ResourceDownloaderListener
{
	public static final int	MAX_FOLLOWS = 1;

	protected ResourceDownloaderBaseImpl		delegate;
	protected boolean							persistent;
	protected File								download_dir;

	protected long						size	= -2;

		// this + clones *share* the torrent object to avoid downloading more than once

	protected TOTorrent[]				torrent_holder	 = new TOTorrent[1];

	protected DownloadManager			download_manager;
	protected Download					download;

	protected boolean					cancelled;
	protected boolean					completed;

	protected ResourceDownloader		current_downloader;
	protected Object					result;
	protected AESemaphore				done_sem	= new AESemaphore("RDTorrent");

	public
	ResourceDownloaderTorrentImpl(
		ResourceDownloaderBaseImpl	_parent,
		ResourceDownloader			_delegate,
		boolean						_persistent,
		File						_download_dir )
	{
		super( _parent );

		persistent		= _persistent;
		download_dir	= _download_dir;
		delegate		= (ResourceDownloaderBaseImpl)_delegate;

		delegate.setParent( this );

		download_manager = PluginInitializer.getDefaultInterface().getDownloadManager();
	}

	@Override
	public String
	getName()
	{
		return( delegate.getName() + ": torrent" );
	}

	@Override
	public long
	getSize()

		throws ResourceDownloaderException
	{
		if ( size == -2 ){

			try{
				size = getSizeSupport();

			}finally{

				if ( size == -2 ){

					size = -1;
				}

				setSize( size );
			}
		}


		return( size );
	}

	@Override
	protected void
	setSize(
		long		l )
	{
		size	= l;

		if ( size >= 0 ){

			delegate.setSize( size );
		}
	}

	@Override
	public void
	setProperty(
		String	name,
		Object	value )

		throws ResourceDownloaderException
	{
		setPropertySupport( name, value );

		delegate.setProperty( name, value );
	}

	protected long
	getSizeSupport()

		throws ResourceDownloaderException
	{
		try{
			if ( torrent_holder[0] == null ){

				ResourceDownloader	x = delegate.getClone( this );

				addReportListener( x );

				InputStream	is = x.download();

				try{
					torrent_holder[0] = TOTorrentFactory.deserialiseFromBEncodedInputStream( is );

				}finally{

					try{
						is.close();

					}catch( IOException e ){
					}
				}

				if( !torrent_holder[0].isSimpleTorrent()){

					throw( new ResourceDownloaderException( this, "Only simple torrents supported" ));
				}
			}

			try{
				String	file_str = new String( torrent_holder[0].getName());

				int	pos = file_str.lastIndexOf( "." );

				String	file_type;

				if ( pos != -1 ){

					file_type = file_str.substring(pos+1);

				}else{

					file_type = null;
				}

				setProperty( 	ResourceDownloader.PR_STRING_CONTENT_TYPE,
								HTTPUtils.guessContentTypeFromFileType( file_type ));

			}catch( Throwable e ){

				Debug.printStackTrace(e);
			}

			return( torrent_holder[0].getSize());

		}catch( TOTorrentException e ){

			throw( new ResourceDownloaderException( this, "Torrent deserialisation failed", e ));
		}
	}

	protected void
	setSizeAndTorrent(
		long			_size,
		TOTorrent[]		_torrent_holder )
	{
		size			= _size;
		torrent_holder	= _torrent_holder;
	}

	@Override
	public ResourceDownloaderBaseImpl
	getClone(
		ResourceDownloaderBaseImpl	parent )
	{
		ResourceDownloaderTorrentImpl c = new ResourceDownloaderTorrentImpl( parent, delegate.getClone( this ), persistent, download_dir );

		c.setSizeAndTorrent( size, torrent_holder );

		c.setProperties( this );

		return( c );
	}

	@Override
	public InputStream
	download()

		throws ResourceDownloaderException
	{
		asyncDownload();

		done_sem.reserve();

		if ( result instanceof InputStream ){

			return((InputStream)result);
		}

		throw((ResourceDownloaderException)result);
	}

	@Override
	public void
	asyncDownload()
	{
		try{
			this_mon.enter();

			if ( cancelled ){

				done_sem.release();

				informFailed((ResourceDownloaderException)result);

			}else{

				if ( torrent_holder[0] == null ){

					current_downloader = delegate.getClone( this );

					informActivity( getLogIndent() + "Downloading: " + getName());

					current_downloader.addListener( this );

					current_downloader.asyncDownload();

				}else{

					downloadTorrent();
				}
			}
		}finally{

			this_mon.exit();
		}
	}

	protected void
	downloadTorrent()
	{
		try{
			String name = new String( torrent_holder[0].getName(), Constants.DEFAULT_ENCODING_CHARSET );

			informActivity( getLogIndent() + "Downloading: " + name );

			TOTorrent	torrent = torrent_holder[0];

			byte[] torrent_hash = torrent.getHash();
			
				// see if already there in an error state and delete if so

			Download existing = null;

			try{
				existing = download_manager.getDownload( torrent_hash );

				if ( existing != null ){

					int	existing_state = existing.getState();

					if ( existing_state == Download.ST_ERROR || existing_state == Download.ST_STOPPED ){

						informActivity( getLogIndent() + "Deleting existing stopped/error state download for " + name );

						existing.remove( true, true );
						
						existing = null;
					}
				}
			}catch( Throwable e ){
				
				informActivity( getLogIndent() + "Failed to tidy up: " + Debug.getNestedExceptionMessage(e));
			}

			File	torrent_file;
			File	data_dir;
			
			if ( existing == null ){
				
					// we *don't* want this temporary file to be deleted automatically as we're
					// going to use it across the client restarts to hold the download data and
					// to seed it afterwards. Therefore we don't use AETemporaryFileHandler.createTempFile!!!!

				torrent_file 	= AETemporaryFileHandler.createSemiTempFile( name + ".torrent" );

				if ( download_dir != null && !download_dir.exists()){

					FileUtil.mkdirs(download_dir);
				}

				data_dir		= download_dir==null?torrent_file.getParentFile():download_dir;

				TorrentUtils.setFlag( torrent, TorrentUtils.TORRENT_FLAG_LOW_NOISE, true );

				boolean anon = isAnonymous();

				torrent.serialiseToBEncodedFile( torrent_file );
			
				DownloadWillBeAddedListener dwbal = null;
	
				try{
					Torrent t = new TorrentImpl(torrent);
	
					String[] networks_to_set;
					
					if ( anon ){
	
						networks_to_set = AENetworkClassifier.AT_NON_PUBLIC;
						
					}else{
						
							// if torrent includes i2p url and i2p installed then enable network
						
						Set<String> hosts = TorrentUtils.getUniqueTrackerHosts( torrent );
						
						boolean	has_i2p = false;
						
						for ( String host: hosts ) {
							
							if ( AENetworkClassifier.categoriseAddress( host ) == AENetworkClassifier.AT_I2P ){
						
								has_i2p = true;
							}
						}
						
						if ( has_i2p && I2PHelpers.isI2PInstalled()){
							
							networks_to_set = new String[]{ AENetworkClassifier.AT_PUBLIC, AENetworkClassifier.AT_I2P, AENetworkClassifier.AT_TOR };

						}else{
						
							networks_to_set = null;
						}
					}
										
					dwbal =
						new DownloadWillBeAddedListener()
						{
								@Override
								public void
								initialised(
									Download download )
								{
									try{
										if ( Arrays.equals( download.getTorrentHash(), torrent.getHash())){

											com.biglybt.core.download.DownloadManager dm = PluginCoreUtils.unwrap( download );
											
											if ( networks_to_set != null ){
											
												dm.getDownloadState().setNetworks( networks_to_set );
											}
											
											try{
												TagManager tm = TagManagerFactory.getTagManager();
												
												TagType tt = tm.getTagType( TagType.TT_DOWNLOAD_MANUAL );
												
												String tag_name = "tag.name.product.updates";
												
												Tag tag = tt.getTag( tag_name, false );
												
												if ( tag == null ){
													
													tag = tt.createTag( tag_name, false );
													
													tag.setImageID( "logo16" );
													
													tt.addTag( tag );
												}
												
												tag.addTaggable( dm );
												
											}catch( Throwable e ){
												
												Debug.out( e );
											}
										}
									}catch( Throwable e ){

										Debug.out( e );
									}
								}
							};
	
					download_manager.addDownloadWillBeAddedListener( dwbal );
						
					if ( persistent ){
	
						download = download_manager.addDownload( t, torrent_file, data_dir );
	
					}else{
	
						download = download_manager.addNonPersistentDownload( t, torrent_file, data_dir );
					}
				}finally{
	
					if ( dwbal != null ){
	
						download_manager.removeDownloadWillBeAddedListener( dwbal );
					}
				}
			}else{
				
				download = existing;
				
				torrent_file = FileUtil.newFile( download.getTorrentFileName());
				
				data_dir = FileUtil.newFile(download.getSavePath()).getParentFile();
			}

			download.moveTo(1);

			download.setForceStart( true );

				// Prevents any move-on-completion or move-on-removal behaviour happening.

			download.setFlag(Download.FLAG_DISABLE_AUTO_FILE_MOVE, true);
			download.setFlag(Download.FLAG_DISABLE_STOP_AFTER_ALLOC, true);

			if ( COConfigurationManager.getBooleanParameter( "Ip Filter Disable For Updates" )){
				
				download.setFlag(Download.FLAG_DISABLE_IP_FILTER, true);
			}

			download_manager.addListener(
				new DownloadManagerListener()
				{
					@Override
					public void
					downloadAdded(
						Download	download )
					{
					}

					@Override
					public void
					downloadRemoved(
						Download	_download )
					{
						if ( download == _download ){

							ResourceDownloaderTorrentImpl.this.downloadRemoved( torrent_file, data_dir );
						}
					}
				});

			download.addListener(
				new DownloadListener()
				{
					@Override
					public void
					stateChanged(
						final Download		download,
						int				old_state,
						int				new_state )
					{
						// System.out.println( "state change:" + old_state + "->" + new_state );

						if ( new_state == Download.ST_SEEDING ){

							download.removeListener( this );

							PluginInitializer.getDefaultInterface().getUtilities().createThread(
								"resource complete event dispatcher",
								new Runnable()
								{
									@Override
									public void
									run()
									{
										downloadSucceeded( download, torrent_file, data_dir );
									}
								});

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
				});

			Thread	t =
				new AEThread( "RDTorrent percentage checker")
				{
					@Override
					public void
					runSupport()
					{
						int	last_percentage = 0;

						while( result == null ){

							int	this_percentage = download.getStats().getDownloadCompleted(false)/10;

							long	total	= torrent.getSize();

							if ( this_percentage != last_percentage ){

								reportPercentComplete( ResourceDownloaderTorrentImpl.this, this_percentage );

								last_percentage = this_percentage;
							}

							try{
								Thread.sleep(1000);

							}catch( Throwable e ){

								Debug.printStackTrace( e );
							}
						}
					}
				};

			t.setDaemon( true );

			t.start();
			
			int state = download.getState();
			
			if ( state == Download.ST_STOPPED ){

					// might have been added-stopped, start if so
		
				download.start();
				
			}else if ( state == Download.ST_SEEDING ){

					// its possible that the d/l has already occurred and it is seeding!

				downloadSucceeded( download, torrent_file, data_dir );
			}
		}catch( Throwable e ){

			failed( this, new ResourceDownloaderException( this, "Torrent download failed", e ));
		}
	}

	protected void
	downloadSucceeded(
		Download	download,
		File		torrent_file,
		File		data_dir )
	{
		synchronized( this ){

			if ( completed ){

				return;
			}

			completed = true;
		}

		reportActivity("Torrent download complete");

			// assumption is that this is a SIMPLE torrent

		File target_file =
			FileUtil.newFile( data_dir,	new String(torrent_holder[0].getFiles()[0].getPathComponents()[0]));

			if ( !target_file.exists()){

				File	actual_target_file = FileUtil.newFile(download.getSavePath());

				try{
					if ( download_dir != null && actual_target_file.exists()){

						FileUtil.copyFile( actual_target_file, target_file );
					}

				}catch( Throwable e ){

					Debug.printStackTrace(e);
				}

				target_file	= actual_target_file;
			}

		try{
			if ( !target_file.exists()){

				throw( new Exception( "File '" + target_file.toString() + "' not found" ));
			}

			InputStream	data = FileUtil.newFileInputStream( target_file );

			informComplete( data );

			result	= data;

			done_sem.release();

		}catch( Throwable e ){

			Debug.printStackTrace( e );

			failed( this, new ResourceDownloaderException( this, "Failed to read downloaded torrent data: " + e.getMessage(), e ));
		}
	}

	protected void
	downloadRemoved(
		File		torrent_file,
		File		data_dir )
	{
		reportActivity( "Torrent removed" );

		if (!( result instanceof InputStream )){

			failed( this, new ResourceDownloaderException( this, "Download did not complete" ));
		}
	}

	@Override
	public void
	cancel()
	{
		setCancelled();

		try{
			this_mon.enter();

			result	= new ResourceDownloaderCancelledException(  this  );

			cancelled	= true;

			informFailed((ResourceDownloaderException)result );

			done_sem.release();

			if ( current_downloader != null ){

				current_downloader.cancel();
			}
		}finally{

			this_mon.exit();
		}
	}

	@Override
	public boolean
	completed(
		ResourceDownloader	downloader,
		InputStream			data )
	{
		try{
			torrent_holder[0] = TOTorrentFactory.deserialiseFromBEncodedInputStream( data );

			if( torrent_holder[0].isSimpleTorrent()){

				downloadTorrent();

			}else{

				failed( this, new ResourceDownloaderException( this, "Only simple torrents supported" ));
			}

		}catch( TOTorrentException e ){

			failed( downloader, new ResourceDownloaderException( this, "Torrent deserialisation failed", e ));

		}finally{

			try{
				data.close();

			}catch( IOException e ){
			}
		}

		return( true );
	}

	@Override
	public void
	failed(
		ResourceDownloader			downloader,
		ResourceDownloaderException e )
	{
		result		= e;

		done_sem.release();

		informFailed(e);
	}

	@Override
	public void
	reportPercentComplete(
		ResourceDownloader	downloader,
		int					percentage )
	{
		if ( downloader == this ){

			informPercentDone( percentage );
		}
	}
}
