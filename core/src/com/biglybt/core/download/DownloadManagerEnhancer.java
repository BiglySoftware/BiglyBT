/*
 * Created on 1 Nov 2006
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


package com.biglybt.core.download;

import java.util.*;

import com.biglybt.core.Core;
import com.biglybt.core.global.GlobalManager;
import com.biglybt.core.global.GlobalManagerListener;
import com.biglybt.core.tag.TagManager;
import com.biglybt.core.tag.TagManagerFactory;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.disk.DiskManagerChannel;
import com.biglybt.pifimpl.local.PluginCoreUtils;
import com.biglybt.pifimpl.local.disk.DiskManagerChannelImpl;
import com.biglybt.util.ExternalStimulusHandler;
import com.biglybt.util.ExternalStimulusListener;

public class
DownloadManagerEnhancer
{
	public static final int	TICK_PERIOD				= 1000;

	private static TagManager					tag_manager = TagManagerFactory.getTagManager();

	private static DownloadManagerEnhancer		singleton;

	public static synchronized DownloadManagerEnhancer
	initialise(
		Core core )
	{
		if ( singleton == null ){

			singleton	= new DownloadManagerEnhancer( core );
		}

		return( singleton );
	}

	public static synchronized DownloadManagerEnhancer
	getSingleton()
	{
		return( singleton );
	}

	Core core;

	Map<DownloadManager,EnhancedDownloadManager>		download_map = new IdentityHashMap<>();

	private Set<HashWrapper>		pause_set = new HashSet<>();

	private boolean			progressive_enabled;

	long				progressive_active_counter;
	TimerEventPeriodic	pa_timer;

	protected
	DownloadManagerEnhancer(
		Core _core )
	{
		core	= _core;

		core.getGlobalManager().addListener(
			new GlobalManagerListener()
			{
				@Override
				public void
				downloadManagerAdded(
					DownloadManager	dm )
				{
					// Don't auto-add to download_map. getEnhancedDownload will
					// take care of it later if we ever need the download
				}

				@Override
				public void
				downloadManagerRemoved(
					DownloadManager	dm )
				{
					EnhancedDownloadManager	edm;

					synchronized( download_map ){

						edm = download_map.remove( dm );
					}

					if ( edm != null ){

						edm.destroy();
					}
				}

				@Override
				public void
				destroyInitiated()
				{
						// resume any downloads we paused

					resume();
				}

				@Override
				public void
				destroyed()
				{
				}

			    @Override
			    public void
			    seedingStatusChanged(
			    	boolean seeding_only_mode, boolean b )
			    {
			    }
			}, false );

		ExternalStimulusHandler.addListener(
			new ExternalStimulusListener()
			{
				@Override
				public boolean
				receive(
					String		name,
					Map			values )
				{
					return( false );
				}

				@Override
				public int
				query(
					String		name,
					Map			values )
				{
					if ( name.equals( "az3.downloadmanager.stream.eta" )){

						Object	hash = values.get( "hash" );

						byte[]	b_hash = null;

						if ( hash instanceof String ){

							String	hash_str = (String)hash;

							if ( hash_str.length() == 32 ){

								b_hash = Base32.decode( hash_str );

							}else{

								b_hash = ByteFormatter.decodeString( hash_str );
							}
						}

						if ( b_hash != null ){

								// ensure we have an enhanced download object for it

							getEnhancedDownload( b_hash );
						}

						List<EnhancedDownloadManager>	edms_copy;

						synchronized( download_map ){

							edms_copy = new ArrayList<>(download_map.values());
						}

						for ( EnhancedDownloadManager edm: edms_copy ){

							if ( b_hash != null ){

								byte[]	d_hash = edm.getHash();

								if ( d_hash != null && Arrays.equals( b_hash, d_hash )){

										// if its complete then obviously 0

									if ( edm.getDownloadManager().isDownloadComplete( false )){

										return( 0 );
									}

									if ( !edm.supportsProgressiveMode()){

										return( Integer.MIN_VALUE );
									}

									if ( !edm.getProgressiveMode()){

										edm.setProgressiveMode( true );
									}

									long eta = edm.getProgressivePlayETA();

									if ( eta > Integer.MAX_VALUE ){

										return( Integer.MAX_VALUE );
									}

									return((int)eta);
								}
							}else{

								if ( edm.getProgressiveMode()){

									long eta = edm.getProgressivePlayETA();

									if ( eta > Integer.MAX_VALUE ){

										return( Integer.MAX_VALUE );
									}

									return((int)eta);
								}
							}
						}
					}

					return( Integer.MIN_VALUE );
				}
			});

			// listener to pick up on streams kicked off externally

		DiskManagerChannelImpl.addListener(
			new DiskManagerChannelImpl.channelCreateListener()
			{
				@Override
				public void
				channelCreated(
					final DiskManagerChannel	channel )
				{
					try{
						final EnhancedDownloadManager edm =
							getEnhancedDownload(
									PluginCoreUtils.unwrap(channel.getFile().getDownload()));

						if ( edm == null ){

							return;
						}

						if ( edm.getDownloadManager().isDownloadComplete( true )){

							return;
						}

						if ( !edm.getProgressiveMode()){

							if ( edm.supportsProgressiveMode()){

								Debug.out( "Enabling progressive mode for '" + edm.getName() + "' due to external stream" );

								edm.setProgressiveMode( true );
							}
						}
					}catch( Throwable e ){

						Debug.printStackTrace(e);
					}
				}
			});
	}

	protected void
	progressiveActivated()
	{
		synchronized( this ){

			progressive_active_counter++;

			if ( pa_timer == null ){

				pa_timer =
					SimpleTimer.addPeriodicEvent(
						"DownloadManagerEnhancer:speedChecker",
						TICK_PERIOD,
						new TimerEventPerformer()
						{
							private int tick_count;

							private long	last_inactive_marker = 0;

							@Override
							public void
							perform(
								TimerEvent event )
							{
								tick_count++;

								long current_marker;

								synchronized( DownloadManagerEnhancer.this ){

									current_marker = progressive_active_counter;

									if ( last_inactive_marker == current_marker ){

										pa_timer.cancel();

										pa_timer = null;

										return;
									}
								}

								List	downloads = core.getGlobalManager().getDownloadManagers();

								boolean	is_active = false;

								for ( int i=0;i<downloads.size();i++){

									DownloadManager download = (DownloadManager)downloads.get(i);

									EnhancedDownloadManager edm = getEnhancedDownload( download );

									if ( edm != null ){

										if ( edm.updateStats( tick_count )){

											is_active = true;
										}
									}
								}

								if ( !is_active ){

									last_inactive_marker = current_marker;
								}
							}
						});
			}
		}
	}

// --Commented out by Inspection START (5/22/2017 2:33 PM):
//	protected Core
//	getCore()
//	{
//		return( core );
//	}
// --Commented out by Inspection STOP (5/22/2017 2:33 PM)

	protected void
	pause(
		DownloadManager		dm )
	{
		TOTorrent torrent = dm.getTorrent();

		if ( torrent == null ){

			return;
		}

		try{
			HashWrapper hw = torrent.getHashWrapper();

			synchronized( pause_set ){

				if ( pause_set.contains( hw )){

					return;
				}

				pause_set.add( hw );
			}

			dm.pause( false );

		}catch( Throwable e ){

			Debug.out( e );
		}
	}

	protected void
	resume(
		DownloadManager		dm )
	{
		TOTorrent torrent = dm.getTorrent();

		if ( torrent == null ){

			return;
		}

		try{
			HashWrapper hw = torrent.getHashWrapper();

			synchronized( pause_set ){

				if ( !pause_set.remove( hw )){

					return;
				}
			}

			dm.resume();

		}catch( Throwable e ){

			Debug.out( e );
		}
	}

	protected void
	resume()
	{
		Set<HashWrapper> copy;

		synchronized( pause_set ){

			copy = new HashSet<>(pause_set);

			pause_set.clear();
		}

		GlobalManager gm = core.getGlobalManager();

		for ( HashWrapper hw: copy ){

			DownloadManager dm = gm.getDownloadManager( hw );

			if ( dm != null ){

				dm.resume();
			}
		}
	}

	protected  void
	prepareForProgressiveMode(
		DownloadManager		dm,
		boolean				active )
	{
		if ( active ){

			GlobalManager gm = core.getGlobalManager();

			List<DownloadManager> dms = (List<DownloadManager>)gm.getDownloadManagers();

			for ( DownloadManager this_dm: dms ){

				if ( this_dm == dm ){

					continue;
				}

				if ( !this_dm.isDownloadComplete(false)){

					int state = this_dm.getState();

					if ( 	state == DownloadManager.STATE_DOWNLOADING ||
							state == DownloadManager.STATE_QUEUED) {

						pause( this_dm );
					}
				}
			}

			if ( dm.isPaused()){

				dm.resume();
			}
		}else{

			resume();
		}
	}

	public EnhancedDownloadManager
	getEnhancedDownload(
		byte[]			hash )
	{
		DownloadManager dm = core.getGlobalManager().getDownloadManager(new HashWrapper( hash ));

		if ( dm == null ){

			return( null );
		}

		return( getEnhancedDownload( dm ));
	}

	public EnhancedDownloadManager
	getEnhancedDownload(
		DownloadManager	manager )
	{
		TOTorrent torrent = manager.getTorrent();

		if ( torrent == null ){

			return( null );
		}

		DownloadManager dm2 = manager.getGlobalManager().getDownloadManager( torrent );

		if ( dm2 != manager ){

			return null;
		}

		synchronized( download_map ){

			EnhancedDownloadManager	res = (EnhancedDownloadManager)download_map.get( manager );

			if ( res == null ){

				res = new EnhancedDownloadManager( DownloadManagerEnhancer.this, manager );

				download_map.put( manager, res );
			}

			return( res );
		}
	}

	public boolean
	isProgressiveAvailable()
	{
		if ( progressive_enabled ){

			return( true );
		}

		PluginInterface	ms_pi = core.getPluginManager().getPluginInterfaceByID( "azupnpav", true );

		if ( ms_pi != null ){

			progressive_enabled = true;
		}

		return( progressive_enabled );
	}

// --Commented out by Inspection START (5/22/2017 2:31 PM):
//	/**
//	 * @param hash
//	 * @return
//	 *
//	 * @since 3.0.1.7
//	 */
//	public DownloadManager findDownloadManager(String hash) {
//		synchronized (download_map) {
//
//			for (Iterator<DownloadManager> iter = download_map.keySet().iterator(); iter.hasNext();) {
//				DownloadManager dm = iter.next();
//
//				TOTorrent torrent = dm.getTorrent();
//				if (PlatformTorrentUtils.isContent(torrent, true)) {
//					String thisHash = PlatformTorrentUtils.getContentHash(torrent);
//					if (hash.equals(thisHash)) {
//						return dm;
//					}
//				}
//			}
//		}
//		return null;
//	}
// --Commented out by Inspection STOP (5/22/2017 2:31 PM)

// --Commented out by Inspection START (5/22/2017 2:32 PM):
//	private void
//	handleAutoTag(
//		List<DownloadManager>	dms )
//	{
//		for ( DownloadManager dm: dms ){
//
//			handleAutoTag( dm );
//		}
//	}
// --Commented out by Inspection STOP (5/22/2017 2:32 PM)

}
