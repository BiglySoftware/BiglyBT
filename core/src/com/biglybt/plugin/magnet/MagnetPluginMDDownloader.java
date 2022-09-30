/*
 * Created on Mar 9, 2012
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


package com.biglybt.plugin.magnet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;

import com.biglybt.core.category.Category;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.networkmanager.Transport;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.torrent.*;
import com.biglybt.core.util.*;
import com.biglybt.pif.PluginInterface;
import com.biglybt.pif.disk.DiskManagerChannel;
import com.biglybt.pif.disk.DiskManagerEvent;
import com.biglybt.pif.disk.DiskManagerListener;
import com.biglybt.pif.disk.DiskManagerRequest;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.download.DownloadAttributeListener;
import com.biglybt.pif.download.DownloadManager;
import com.biglybt.pif.download.DownloadManagerListener;
import com.biglybt.pif.download.DownloadPeerListener;
import com.biglybt.pif.peers.*;
import com.biglybt.pif.utils.PooledByteBuffer;
import com.biglybt.pifimpl.local.PluginCoreUtils;

public class
MagnetPluginMDDownloader
{
	final private static Object			ACTIVE_SET_LOCK = new Object();
	
	final private static Set<String>	active_set = new HashSet<>();

	final private Object			INSTANCE_LOCK = new Object();

	final private PluginInterface		plugin_interface;
	final private MagnetPlugin			plugin;
	final private byte[]				hash;
	final private Set<String>			networks;
	final private InetSocketAddress[]	addresses;
	final private List<String>			tags;
	final private Map<String,Object>	initial_metadata;
	final private String				args;

	final DownloadActivity				activity;
	
	private volatile com.biglybt.core.download.DownloadManager		core_dm;
	
	private volatile boolean		started;
	private volatile boolean		cancelled;
	private volatile boolean		completed;

	private List<DiskManagerRequest>	requests = new ArrayList<>();

	private boolean		run_complete;
	private AESemaphore complete_sem 	= new AESemaphore( "MPMDD:comp" );

	protected
	MagnetPluginMDDownloader(
		MagnetPlugin		_plugin,
		PluginInterface		_plugin_interface,
		byte[]				_hash,
		Set<String>			_networks,
		InetSocketAddress[]	_addresses,
		List<String>		_tags,
		Map<String,Object>	_initial_metadata,
		String				_args )
	{
		plugin				= _plugin;
		plugin_interface	= _plugin_interface;
		hash				= _hash;
		networks			= _networks;
		addresses			= _addresses;
		tags				= _tags;
		initial_metadata	= _initial_metadata;
		args				= _args;
		
		activity = new DownloadActivity();
	}

	protected void
	start(
		final DownloadListener		listener )
	{
		synchronized( INSTANCE_LOCK ){

			if ( started ){

				listener.failed( false, new Exception( "Already started" ));

				return;
			}

			if ( cancelled || completed ){

				listener.failed( false, new Exception( "Already cancelled/completed" ));

				return;
			}

			started = true;

			new AEThread2( "MagnetPluginMDDownloader" )
			{
				@Override
				public void
				run()
				{
					activity.start( listener );
				}
			}.start();
		}
	}

	protected boolean
	cancel()
	{
		return( activity.cancel( false ));
	}

	protected void
	update()
	{
		activity.update();
	}
	
	protected com.biglybt.core.download.DownloadManager
	getDownloadManager()
	{
		return( core_dm );
	}
		
	private class
	DownloadActivity
	{
		final String	hash_str = ByteFormatter.encodeString( hash );

		final Map<String,String>	magnet_args = new HashMap<>();

		final String		display_name;
		final List<String>	trackers 	= new ArrayList<>();

		File md_dir 		= null;
		File data_file 		= null;
		File torrent_file 	= null;

		DownloadManager download_manager = plugin_interface.getDownloadManager();

		DownloadManagerListener	download_manager_listener = null;
		
		Download download	= null;

		final Set<String> peer_networks = new HashSet<>();
		
		final List<Map<String,Object>> peers_for_cache = new ArrayList<>();

		final Throwable[] 	error 				= { null };
		final boolean[]		manually_removed 	= { false };
		
		final ByteArrayOutputStream	result = new ByteArrayOutputStream(32*1024);

		TOTorrentAnnounceURLSet[]	url_sets = null;

		DownloadListener	activity_listener;
		boolean				activity_listener_informed = false;
		
		long last_update = -1;
		
		DownloadActivity()
		{
			String[] bits = args.split( "&" );
			
			String	name = "magnet:" + Base32.encode( hash );
	
			for ( String bit: bits ){

				String[] x = bit.split( "=" );

				if ( x.length == 2 ){

					String lhs = x[0].toLowerCase();
					String rhs =  UrlUtils.decode( x[1] );

					magnet_args.put( lhs, rhs );

					if ( lhs.equals( "tr" )){

						String tracker = rhs;

						trackers.add( tracker );

					}else if ( lhs.equals( "dn" )){

						name = rhs;
					}
				}
			}
			
			display_name = MessageText.getString( "MagnetPlugin.use.md.download.name", new String[]{ name }) + ".torrent";
		}
		
		void
		start(
			DownloadListener		_listener )
		{
			activity_listener = _listener;
			
			boolean setup_started	= false;
			boolean setup_complete	= false;
			
			try{
				synchronized( ACTIVE_SET_LOCK ){
	
					if ( active_set.contains( hash_str )){
	
						throw( new Exception( "Download already active for hash " + hash_str ));
					}
	
					active_set.add( hash_str );
				}
	
				Download existing_download = download_manager.getDownload( hash );
	
				if ( existing_download != null ){
	
					throw( new Exception( "download already exists" ));
				}
	
				setup_started = true;

				File torrents_dir = FileUtil.getUserFile( "torrents" );
	
				md_dir = FileUtil.newFile( torrents_dir, "md", hash_str );
				
				if ( !md_dir.exists()){
					
					md_dir.mkdirs();
				}
				
				data_file 		= FileUtil.newFile( md_dir, hash_str + ".torrent" );
				torrent_file 	= FileUtil.newFile( md_dir, hash_str + ".metatorrent" );
	
				int MD_TORRENT_SIZE = 64*1024*1024;
				
				if ( !data_file.exists() || data_file.length() != MD_TORRENT_SIZE ){
				
					RandomAccessFile raf = new RandomAccessFile( data_file, "rw" );
	
					try{
						byte[] buffer = new byte[512*1024];
		
						Arrays.fill( buffer, (byte)0xff );
		
						for (long i=0;i<MD_TORRENT_SIZE;i+=buffer.length){
		
							raf.write( buffer );
						}
					}finally{
		
						raf.close();
					}
				}
				
				URL announce_url = TorrentUtils.getDecentralisedURL( hash );
	
				TOTorrent meta_torrent = null;
				
				if ( torrent_file.exists()){
					
					try{
						meta_torrent = TOTorrentFactory.deserialiseFromBEncodedFile( torrent_file );
						
					}catch( Throwable e ){
						
						Debug.out( e );
					}
				}
				
				if ( meta_torrent == null ){
					
					TOTorrentCreator creator =
						TOTorrentFactory.createFromFileOrDirWithFixedPieceLength(
								data_file,
								announce_url,
								16*1024 );
		
					meta_torrent = creator.create( true );
				}
	

				List<String>	extras = plugin.getExtraTrackers();
							
				for ( String extra: extras ){
					
					try{
						if ( trackers.contains( extra )){
							
							continue;
						}
						
						URL url = new URL( extra );
						
						String net = AENetworkClassifier.categoriseAddress( url.getHost());
						
						if ( net == AENetworkClassifier.AT_PUBLIC ){
							
							if ( networks.isEmpty() || networks.contains( AENetworkClassifier.AT_PUBLIC )){
								
								trackers.add( extra );
							}
						}else if ( networks.contains( net )){
							
							trackers.add( extra );
						}
					}catch( Throwable e ){
						
					}
				}
					
				if ( trackers.size() > 0 ){
	
						// stick the decentralised one we created above in position 0 - this will be
						// removed later if the torrent is downloaded
	
					trackers.add( 0, announce_url.toExternalForm());
	
					TOTorrentAnnounceURLGroup ag = meta_torrent.getAnnounceURLGroup();
	
					List<TOTorrentAnnounceURLSet> sets = new ArrayList<>();
	
					for ( String tracker: trackers ){
	
						try{
							URL tracker_url =  new URL( tracker );
	
							sets.add( ag.createAnnounceURLSet(new URL[]{ tracker_url }));
	
						}catch( Throwable e ){
	
							Debug.out( e );
						}
					}
	
					if ( sets.size() > 0 ){
	
						url_sets = sets.toArray( new TOTorrentAnnounceURLSet[ sets.size()]);
	
						ag.setAnnounceURLSets( url_sets );
					}
				}
	
				meta_torrent.setHashOverride( hash );
	
				TorrentUtils.setFlag( meta_torrent, TorrentUtils.TORRENT_FLAG_METADATA_TORRENT, true );
	
				TorrentUtils.setFlag( meta_torrent, TorrentUtils.TORRENT_FLAG_LOW_NOISE, true );
	
				meta_torrent.serialiseToBEncodedFile( torrent_file );
	
				download_manager.clearNonPersistentDownloadState( hash );
	
				download = download_manager.addNonPersistentDownloadStopped( PluginCoreUtils.wrap( meta_torrent ), torrent_file, data_file);
		
				core_dm = PluginCoreUtils.unwrap( download );
				
				DownloadManagerState state = core_dm.getDownloadState();
				
				TorrentUtils.setResumeDataTotallyIncomplete( state );	// avoid any initial rechecking
				
				state.setDisplayName( display_name );
	
				if (	 networks.size() == 0 ||
						( networks.size() == 1 && networks.contains( AENetworkClassifier.AT_PUBLIC ))){
	
						// no clues in the magnet link, or just public
						// start off by enabling all networks, public will be disabled later
						// if off by default
	
					for ( String network: AENetworkClassifier.AT_NETWORKS ){
	
						state.setNetworkEnabled( network, true );
					}
	
				}else{
	
					for ( String network: networks ){
	
						state.setNetworkEnabled( network, true );
					}
	
						// disable public network if no explicit trackers are public ones
	
					if ( !networks.contains( AENetworkClassifier.AT_PUBLIC )){
	
						state.setNetworkEnabled( AENetworkClassifier.AT_PUBLIC, false );
					}
				}
	
					// if user has specifically disabled the public network then remove this too
					// as this gives them a way to control metadata download network usage
	
				if ( !plugin.isNetworkEnabled( AENetworkClassifier.AT_PUBLIC )){
	
					state.setNetworkEnabled( AENetworkClassifier.AT_PUBLIC, false );
				}
	
				final List<InetSocketAddress>	peers_to_inject = new ArrayList<>();
	
				if ( addresses != null && addresses.length > 0 ){
	
					String[] enabled_nets = state.getNetworks();
	
					for ( InetSocketAddress address: addresses ){
	
						String host = AddressUtils.getHostAddress( address );
	
						String net = AENetworkClassifier.categoriseAddress( host );
	
						for ( String n: enabled_nets ){
	
							if ( n == net ){
	
								peers_to_inject.add( address );
	
								break;
							}
						}
					}
				}
				
				state.addListener(
					(d,a,t)->{
						plugin.setDNChanged( core_dm );
					},
					DownloadManagerState.AT_DISPLAY_NAME,
					DownloadAttributeListener.WRITTEN );
	
				plugin.setInitialMetadata( core_dm, tags, initial_metadata );
		
				download.addPeerListener(
					new DownloadPeerListener()
					{
						@Override
						public void
						peerManagerAdded(
							final Download			download,
							final PeerManager		peer_manager )
						{
							if ( cancelled || completed ){
	
								download.removePeerListener( this );
	
								return;
							}
	
							final PEPeerManager pm = PluginCoreUtils.unwrap( peer_manager );
	
							peer_manager.addListener(
								new PeerManagerListener2()
								{
									private PeerManagerListener2	pm_listener = this;
	
									private int	md_size;
	
									@Override
									public void
									eventOccurred(
										PeerManagerEvent	event )
									{
										if ( cancelled || completed ){
	
											peer_manager.removeListener( this );
	
											return;
										}
	
										if ( event.getType() != PeerManagerEvent.ET_PEER_ADDED ){
	
											return;
										}
	
										final Peer peer = event.getPeer();
	
										try{
											String	peer_ip = peer.getIp();
	
											String network = AENetworkClassifier.categoriseAddress( peer_ip );
	
											synchronized( INSTANCE_LOCK ){
	
												peer_networks.add( network );
	
												Map<String,Object> map = new HashMap<>();
	
												peers_for_cache.add( map );
	
												map.put( "ip", peer_ip.getBytes( "UTF-8" ));
	
												map.put( "port", new Long(peer.getPort()));
											}
										}catch( Throwable e ){
	
											Debug.out( e );
										}
	
										peer.addListener(
											new PeerListener2()
											{
												@Override
												public void
												eventOccurred(
													PeerEvent	event )
												{
													if ( cancelled || completed || md_size > 0 ){
	
														peer.removeListener( this );
	
														return;
													}
	
													if ( event.getType() != PeerEvent.ET_STATE_CHANGED ){
	
														return;
													}
	
													if ( (Integer)event.getData() != Peer.TRANSFERING ){
	
														return;
													}
	
													synchronized( INSTANCE_LOCK ){
	
														if ( md_size > 0 ){
	
															return;
														}
	
														md_size = pm.getTorrentInfoDictSize();
	
														if ( md_size > 0 ){
	
															peer_manager.removeListener( pm_listener );
	
														}else{
	
															return;
														}
													}
	
													reportProgress( 0, md_size );
	
													new AEThread2( "" )
													{
														@Override
														public void
														run()
														{
															DiskManagerChannel channel = null;
	
															try{
																channel = download.getDiskManagerFileInfo()[0].createChannel();
	
																final DiskManagerRequest request = channel.createRequest();
	
																request.setType( DiskManagerRequest.REQUEST_READ );
																request.setOffset( 0 );
																request.setLength( md_size );
	
																request.setMaximumReadChunkSize( 16*1024 );
	
																request.addListener(
																	new DiskManagerListener()
																	{
																		@Override
																		public void
																		eventOccurred(
																			DiskManagerEvent	event )
																		{
																			int	type = event.getType();
	
																			if ( type == DiskManagerEvent.EVENT_TYPE_FAILED ){
	
																				error[0]	= event.getFailure();
	
																				runComplete();
	
																			}else if ( type == DiskManagerEvent.EVENT_TYPE_SUCCESS ){
	
																				PooledByteBuffer	buffer = null;
	
																				try{
																					buffer	= event.getBuffer();
	
																					byte[]	bytes = buffer.toByteArray();
	
																					int	dl_size;
	
																					synchronized( INSTANCE_LOCK ){
	
																						result.write( bytes );
	
																						dl_size = result.size();
	
																						if ( dl_size == md_size ){
	
																							completed	= true;
	
																							reportProgress( md_size, md_size );
	
																							runComplete();
																						}
																					}
	
																					if ( !completed ){
	
																						reportProgress( dl_size, md_size );
																					}
	
																				}catch( Throwable e ){
	
																					error[0] = e;
	
																					request.cancel();
	
																					runComplete();
	
																				}finally{
	
																					if ( buffer != null ){
	
																						buffer.returnToPool();
																					}
																				}
																			}else if ( type == DiskManagerEvent.EVENT_TYPE_BLOCKED ){
	
																				//System.out.println( "Waiting..." );
																			}
																		}
																	});
	
																synchronized( INSTANCE_LOCK ){
	
																	if ( cancelled ){
	
																		return;
																	}
	
																	requests.add( request );
																}
	
																request.run();
	
																synchronized( INSTANCE_LOCK ){
	
																	requests.remove( request );
																}
															}catch( Throwable e ){
	
																error[0] = e;
	
																runComplete();
	
															}finally{
	
																if ( channel != null ){
	
																	channel.destroy();
																}
															}
														}
													}.start();
												}
											});
									}
								});
						}
	
						@Override
						public void
						peerManagerRemoved(
							Download		download,
							PeerManager		peer_manager )
						{
						}
					});
	
				final Download f_download = download;
	
				DownloadManagerListener dl_listener =
					new DownloadManagerListener()
					{	
						private TimerEventPeriodic	timer_event;
						private boolean				removed;
	
						@Override
						public void
						downloadAdded(
							final Download	download )
						{
							if ( download == f_download ){
	
								synchronized( INSTANCE_LOCK ){
	
									if ( !removed ){
	
										if ( timer_event == null ){
	
											timer_event =
												SimpleTimer.addPeriodicEvent(
													"announcer",
													30*1000,
													new TimerEventPerformer()
													{
														@Override
														public void
														perform(
															TimerEvent event)
														{
															synchronized( INSTANCE_LOCK ){
	
																if ( removed ){
	
																	return;
																}
	
																	// it is possible for the running_sem to be released before
																	// the downloadRemoved event is fired and the listener removed
																	// so the removed event never fires...
	
																if (  run_complete ){
	
																	if ( timer_event != null ){
	
																		timer_event.cancel();
	
																		timer_event = null;
																	}
	
																	return;
																}
															}
	
															download.requestTrackerAnnounce( true );
	
															injectPeers( download );
														}
													});
										}
	
										if ( peers_to_inject.size() > 0 ){
	
											SimpleTimer.addEvent(
												"injecter",
												SystemTime.getOffsetTime( 5*1000 ),
												new TimerEventPerformer(){
													@Override
													public void perform(TimerEvent event ){
														injectPeers( download );
													}
												});
										}
									}
								}
							}
						}
	
						private void
						injectPeers(
							Download	download )
						{
							PeerManager pm = download.getPeerManager();
	
							if ( pm != null ){
	
								for ( InetSocketAddress address: peers_to_inject ){
	
									pm.addPeer(
										AddressUtils.getHostAddress( address ),
										address.getPort());
								}
							}
						}
	
						@Override
						public void
						downloadRemoved(
							Download	dl )
						{
							if ( dl == f_download ){
	
								synchronized( INSTANCE_LOCK ){
	
									removed = true;
	
									if ( timer_event != null ){
	
										timer_event.cancel();
	
										timer_event = null;
									}
								}
	
								if ( !( cancelled || completed )){
	
									error[0] = new Exception( "Download manually removed" );
	
									manually_removed[0] = true;
									
									runComplete();
								}
							}
						}
					};
	
				download_manager.addListener( dl_listener, true );
				
				download_manager_listener = dl_listener;
				
				synchronized( INSTANCE_LOCK ){
					
					Download[] existing = download_manager.getDownloads();
	
					// add this one after the last existing metadata download
	
					int	move_to = 1;
	
					for ( Download e: existing ){
	
						// we check force-start here as we don't want to pick up other metadata downloads
						// in the process of being added that have yet to be moved to the correct location
						// via the code below
	
						if ( e == download || !e.isForceStart()){
	
							continue;
						}
	
						if ( e.getFlag( Download.FLAG_METADATA_DOWNLOAD )){
	
							move_to = Math.max( move_to, e.getPosition()+1 );
						}
					}
	
					download.moveTo( move_to );
	
					download.setForceStart( true );
	
					download.setFlag( Download.FLAG_DISABLE_AUTO_FILE_MOVE, true );
				}
				
				setup_complete = true;
				
			}catch( Throwable e ){
	
				boolean	was_cancelled = cancelled;
	
				cancel( true );
	
				if ( !was_cancelled ){
	
					reportFailed( manually_removed[0], e );
	
					Debug.out( e );
				}
			}finally{
	
				if ( setup_started && !setup_complete ){
				
					tidyUp();
				}
			}
		}
		
		void
		update()
		{
			long now = SystemTime.getMonotonousTime();
			
			if ( last_update == -1 || now - last_update > 30*1000 ){
				
				last_update = now;
			
				try{
					Peer[] peers = download.getPeerManager().getPeers();
					
					for ( Peer peer: peers ){
						
						PEPeer pe_peer = PluginCoreUtils.unwrap( peer );
						
						if ( pe_peer instanceof PEPeerTransport ){
						
							PEPeerTransport pt = (PEPeerTransport)pe_peer;
											
							long connected_for = pt.getTimeSinceConnectionEstablished();
						
							long last_good_data = pt.getTimeSinceGoodDataReceived();
							
							if ( connected_for > 3*60*1000 ){
								
								if ( last_good_data == -1 || last_good_data > 3*60*1000 ){
							
									pt.getManager().removePeer( pt, "Metadata dead peer removal", Transport.CR_TIMEOUT_ACTIVITY );
								}
							}		
						}
					}
				}catch( Throwable e ){		
				}
			}
		}
		
		boolean
		cancel(
			boolean	internal )
		{
			boolean	wait_for_complete 	= !internal;

			try{
				List<DiskManagerRequest>	to_cancel;

				synchronized( INSTANCE_LOCK ){

					if ( !started ){

						Debug.out( "Not started!" );

						wait_for_complete 	= false;
					}

					if ( cancelled || completed ){

						return( cancelled );
					}

					cancelled	= true;

					to_cancel = new ArrayList<>(requests);

					requests.clear();
				}

				for ( DiskManagerRequest request: to_cancel ){

					request.cancel();
				}
				
				return( true );
				
			}finally{

				runComplete();

				if ( wait_for_complete ){

					complete_sem.reserve();
				}
			}
		}
		
		private void
		runComplete()
		{
			synchronized( INSTANCE_LOCK ){
				
				if ( run_complete ){
					
					return;
				}
				
				run_complete = true;
			}
			
			try{
				if ( completed ){
					
					byte[]	bytes = result.toByteArray();
	
					Map<String,Object>	info = BDecoder.decode( bytes );
	
					Map<String,Object>	map = new HashMap<>();
	
					map.put( "info", info );
	
					TOTorrent torrent = TOTorrentFactory.deserialiseFromMap( map );
	
					byte[] torrent_hash = torrent.getHash();
	
					if ( !Arrays.equals( hash, torrent_hash )){
	
						byte[] truncated_v2_hash = torrent.getTruncatedHash( TOTorrent.TT_V2 );
						
						boolean ok = false;
						
						if ( truncated_v2_hash != null ){
						
							ok = Arrays.equals( hash, truncated_v2_hash );
						}
						
						if ( !ok ){
						
							throw( new Exception( "Metadata torrent hash mismatch: expected=" + ByteFormatter.encodeString( hash ) + ", actual=" + ByteFormatter.encodeString( torrent_hash )));
						}
					}
	
					if ( url_sets != null ){
	
							// first entry should be the decentralised one that we want to remove now
	
						List<TOTorrentAnnounceURLSet> updated = new ArrayList<>();
	
						for ( TOTorrentAnnounceURLSet set: url_sets ){
	
							if ( !TorrentUtils.isDecentralised( set.getAnnounceURLs()[0] )){
	
								updated.add( set );
							}
						}
	
						if ( updated.size() == 0 ){
	
							url_sets = null;
	
						}else{
	
							url_sets = updated.toArray( new TOTorrentAnnounceURLSet[updated.size()]);
						}
					}
	
					if ( url_sets != null ){
	
						torrent.setAnnounceURL( url_sets[0].getAnnounceURLs()[0] );
	
						torrent.getAnnounceURLGroup().setAnnounceURLSets( url_sets );
	
					}else{
	
						torrent.setAnnounceURL( TorrentUtils.getDecentralisedURL( hash ));
					}
	
					if ( peers_for_cache.size() > 0 ){
	
						Map<String,List<Map<String,Object>>> peer_cache = new HashMap<>();
	
						peer_cache.put( "tracker_peers", peers_for_cache );
	
						TorrentUtils.setPeerCache( torrent, peer_cache );
					}
					
					String current_dn = core_dm.getDownloadState().getDisplayName();
					
					if ( !current_dn.equals( display_name )){
						
						TorrentUtils.setDisplayName( torrent, current_dn );
					}
	
					try{
						String dn		= magnet_args.get( "dn" );
	
						if ( dn != null ){
	
							PlatformTorrentUtils.setContentTitle( torrent, dn );
						}
	
						String pfi_str = magnet_args.get( "pfi" );
	
						if ( pfi_str != null ){
	
							PlatformTorrentUtils.setContentPrimaryFileIndex( torrent, Integer.parseInt( pfi_str ));
						}
					}catch( Throwable e ){
					}
					
					reportComplete( torrent, peer_networks );
	
				}else{
						
					if ( cancelled ){
	
						throw( new Exception( "Download cancelled" ));
	
					}else{
	
						cancel( true );
	
						try{
							if ( error[0] != null ){
	
								throw( error[0] );
	
							}else{
	
								throw( new Exception( "Download terminated prematurely" ));
							}
						}catch( Throwable e ){
	
							reportFailed( manually_removed[0],  e );
		
							throw( e );
						}
					}
				}
			}catch( Throwable e ){
				
				boolean	was_cancelled = cancelled;
	
				cancel( true );
	
				if ( !was_cancelled ){
	
					reportFailed( manually_removed[0], e );
	
					Debug.out( e );
				}
			}finally{
				
				tidyUp();
			}
		}
		
		private void
		tidyUp()
		{
			try{
				if ( download_manager_listener != null ){
					
					download_manager.removeListener( download_manager_listener );
				}
				
				if ( download != null ){

						// unfortunately the tags and category get lost on download removal so cache them
					
					List<String> latest_tags =  plugin.getInitialTags( core_dm );
					
					if ( !latest_tags.isEmpty()){
					
						core_dm.setUserData( MagnetPlugin.DM_TAG_CACHE, latest_tags );
					}
					
					Category cat = core_dm.getDownloadState().getCategory();
					
					if ( cat != null ){
						
						if ( cat.getType() == Category.TYPE_USER ){

							core_dm.setUserData( MagnetPlugin.DM_CATEGORY_CACHE, cat.getName());
						}
					}
					
					try{
						download.stopAndRemove(false, false);

					}catch( Throwable e ){

						Debug.out( e );
					}
					
					download = null;
				}

				List<DiskManagerRequest>	to_cancel;

				synchronized( INSTANCE_LOCK ){

					to_cancel = new ArrayList<>(requests);

					requests.clear();
				}

				for ( DiskManagerRequest request: to_cancel ){

					request.cancel();
				}

				if ( torrent_file != null ){

					torrent_file.delete();
				}

				if ( data_file != null ){

					data_file.delete();
				}

				if ( md_dir != null ){

					FileUtil.recursiveDeleteNoCheck( md_dir );
				}
			}catch( Throwable e ){

				Debug.out( e );

			}finally{

				synchronized( ACTIVE_SET_LOCK ){

					active_set.remove( hash_str );
				}

				complete_sem.releaseForever();
			}
		}
		
		private void 
		reportProgress(
			int downloaded,	
			int total_size ) 
		{
			DownloadManagerState downloadState = core_dm.getDownloadState();
			
			if (downloaded == 0 && total_size > 0) {
				
				downloadState.setLongAttribute("magnet.torrent.size", total_size);
			}
			
			downloadState.setLongAttribute("magnet.torrent.downloaded", downloaded);

			activity_listener.reportProgress(downloaded, total_size);
		}

		private void
		reportFailed(
			boolean		manual_cancel,
			Throwable	e )
		{
			synchronized( INSTANCE_LOCK ){
				
				if ( activity_listener_informed ){
					
					return;
				}
				
				activity_listener_informed = true;
			}
			
			activity_listener.failed(manual_cancel, e );
		}
		
		private void
		reportComplete(
			TOTorrent		torrent,
			Set<String>		peer_networks )
		{
			synchronized( INSTANCE_LOCK ){
				
				if ( activity_listener_informed ){
					
					return;
				}
				
				activity_listener_informed = true;
			}
			
			activity_listener.complete(torrent, peer_networks);
		}
	}
	
	protected interface
	DownloadListener
	{
		public void
		reportProgress(
			int		downloaded,
			int		total_size );
		
		public void
		complete(
			TOTorrent		torrent,
			Set<String>		peer_networks );

		public void
		failed(
			boolean		manually_cancelled,
			Throwable 	e );
	}
}
